package com.khayom1.missminutes.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

data class ConversationSummary(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int
)

data class StoredConversationMessage(
    val id: Long,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: Long
)

data class MemoryItem(
    val id: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
)


enum class RestoreConflictPolicy { MERGE, REPLACE, KEEP_EXISTING }

data class RestoreSummary(
    val conversationsImported: Int,
    val conversationsSkipped: Int,
    val messagesImported: Int,
    val memoriesImported: Int
)

class ConversationLimitReachedException : IllegalStateException(
    "This conversation reached its message limit. Start a new conversation to continue."
)

/**
 * Offline-first local store. Message and memory content is encrypted with
 * Android Keystore AES-256-GCM before insertion; only ciphertext is stored in
 * SQLite. All reads and writes are scoped to the local workspace owner id.
 */
class LocalConversationStore(
    context: Context,
    private val cipher: ConversationCipher = AndroidKeystoreConversationCipher(),
    databaseName: String = DATABASE_NAME
) : SQLiteOpenHelper(context.applicationContext, databaseName, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        db.rawQuery("PRAGMA secure_delete = ON", null).use { cursor -> cursor.moveToFirst() }
    }

    override fun onCreate(db: SQLiteDatabase) {
        ConversationSchema.createVersionOne(db)
        ConversationSchema.upgradeToVersionTwo(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2 && newVersion >= 2) {
            ConversationSchema.upgradeToVersionTwo(db)
        }
    }

    @Synchronized
    fun createConversation(
        ownerId: String,
        title: String = DEFAULT_TITLE,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        validateOwner(ownerId)
        require(title.isNotBlank() && title.length <= MAX_TITLE_CHARS)
        val existingCount = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM conversations WHERE owner_id = ?",
            arrayOf(ownerId)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
        check(existingCount < MAX_CONVERSATIONS) { "The local conversation limit has been reached." }
        val id = UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put("id", id)
            put("owner_id", ownerId)
            put("title_ciphertext", cipher.encrypt(ownerId, title))
            put("created_at", nowMillis)
            put("updated_at", nowMillis)
        }
        writableDatabase.insertOrThrow("conversations", null, values)
        return id
    }

    @Synchronized
    fun listConversations(ownerId: String, limit: Int = MAX_CONVERSATIONS): List<ConversationSummary> {
        validateOwner(ownerId)
        require(limit in 1..MAX_CONVERSATIONS)
        val result = mutableListOf<ConversationSummary>()
        readableDatabase.rawQuery(
            """
            SELECT c.id, c.title_ciphertext, c.created_at, c.updated_at, COUNT(m.id) AS message_count
            FROM conversations c
            LEFT JOIN messages m ON m.conversation_id = c.id AND m.owner_id = c.owner_id
            WHERE c.owner_id = ?
            GROUP BY c.id
            ORDER BY c.updated_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(ownerId, limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += ConversationSummary(
                    id = cursor.getString(0),
                    title = cipher.decrypt(ownerId, cursor.getString(1)),
                    createdAt = cursor.getLong(2),
                    updatedAt = cursor.getLong(3),
                    messageCount = cursor.getInt(4)
                )
            }
        }
        return result
    }

    @Synchronized
    fun getMessages(ownerId: String, conversationId: String): List<StoredConversationMessage> {
        validateOwner(ownerId)
        require(conversationId.isNotBlank())
        val result = mutableListOf<StoredConversationMessage>()
        readableDatabase.query(
            "messages",
            arrayOf("id", "conversation_id", "role", "content_ciphertext", "created_at"),
            "owner_id = ? AND conversation_id = ?",
            arrayOf(ownerId, conversationId),
            null,
            null,
            "id ASC",
            MAX_MESSAGES_PER_CONVERSATION.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += StoredConversationMessage(
                    id = cursor.getLong(0),
                    conversationId = cursor.getString(1),
                    role = cursor.getString(2),
                    content = cipher.decrypt(ownerId, cursor.getString(3)),
                    createdAt = cursor.getLong(4)
                )
            }
        }
        return result
    }

    @Synchronized
    fun appendMessage(
        ownerId: String,
        conversationId: String,
        role: String,
        content: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        validateOwner(ownerId)
        require(conversationId.isNotBlank())
        require(role == ROLE_USER || role == ROLE_ASSISTANT)
        require(content.isNotBlank() && content.length <= MAX_MESSAGE_CHARS)

        val db = writableDatabase
        db.beginTransaction()
        try {
            val titleAndCount = db.rawQuery(
                "SELECT title_ciphertext, (SELECT COUNT(*) FROM messages WHERE conversation_id = ? AND owner_id = ?) FROM conversations WHERE id = ? AND owner_id = ?",
                arrayOf(conversationId, ownerId, conversationId, ownerId)
            ).use { cursor ->
                check(cursor.moveToFirst()) { "Conversation does not exist for this account." }
                cipher.decrypt(ownerId, cursor.getString(0)) to cursor.getInt(1)
            }

            if (titleAndCount.second >= MAX_MESSAGES_PER_CONVERSATION) {
                throw ConversationLimitReachedException()
            }
            val values = ContentValues().apply {
                put("conversation_id", conversationId)
                put("owner_id", ownerId)
                put("role", role)
                put("content_ciphertext", cipher.encrypt(ownerId, content))
                put("created_at", nowMillis)
            }
            val id = db.insertOrThrow("messages", null, values)

            if (titleAndCount.second == 0 && role == ROLE_USER) {
                val title = content.replace(Regex("\\s+"), " ").trim().take(MAX_TITLE_CHARS)
                db.update(
                    "conversations",
                    ContentValues().apply {
                        put("title_ciphertext", cipher.encrypt(ownerId, title))
                        put("updated_at", nowMillis)
                    },
                    "id = ? AND owner_id = ?",
                    arrayOf(conversationId, ownerId)
                )
            } else {
                db.update(
                    "conversations",
                    ContentValues().apply { put("updated_at", nowMillis) },
                    "id = ? AND owner_id = ?",
                    arrayOf(conversationId, ownerId)
                )
            }

            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun deleteConversation(ownerId: String, conversationId: String): Boolean {
        validateOwner(ownerId)
        require(conversationId.isNotBlank())
        return writableDatabase.delete(
            "conversations",
            "id = ? AND owner_id = ?",
            arrayOf(conversationId, ownerId)
        ) > 0
    }

    @Synchronized
    fun deleteAll(ownerId: String): Int {
        validateOwner(ownerId)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val conversations = db.delete("conversations", "owner_id = ?", arrayOf(ownerId))
            val memories = db.delete("memory_items", "owner_id = ?", arrayOf(ownerId))
            db.setTransactionSuccessful()
            return conversations + memories
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun addMemory(
        ownerId: String,
        content: String,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        validateOwner(ownerId)
        val normalized = content.trim()
        require(normalized.isNotEmpty() && normalized.length <= MAX_MEMORY_CHARS)
        val existingCount = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM memory_items WHERE owner_id = ?",
            arrayOf(ownerId)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
        check(existingCount < MAX_MEMORY_ITEMS) { "The local memory limit has been reached." }
        val id = UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put("id", id)
            put("owner_id", ownerId)
            put("content_ciphertext", cipher.encrypt(ownerId, normalized))
            put("created_at", nowMillis)
            put("updated_at", nowMillis)
        }
        writableDatabase.insertOrThrow("memory_items", null, values)
        return id
    }

    @Synchronized
    fun listMemory(ownerId: String, limit: Int = MAX_MEMORY_ITEMS): List<MemoryItem> {
        validateOwner(ownerId)
        require(limit in 1..MAX_MEMORY_ITEMS)
        val result = mutableListOf<MemoryItem>()
        readableDatabase.query(
            "memory_items",
            arrayOf("id", "content_ciphertext", "created_at", "updated_at"),
            "owner_id = ?",
            arrayOf(ownerId),
            null,
            null,
            "updated_at DESC",
            limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += MemoryItem(
                    id = cursor.getString(0),
                    content = cipher.decrypt(ownerId, cursor.getString(1)),
                    createdAt = cursor.getLong(2),
                    updatedAt = cursor.getLong(3)
                )
            }
        }
        return result
    }

    @Synchronized
    fun deleteMemory(ownerId: String, memoryId: String): Boolean {
        validateOwner(ownerId)
        require(memoryId.isNotBlank())
        return writableDatabase.delete(
            "memory_items",
            "id = ? AND owner_id = ?",
            arrayOf(memoryId, ownerId)
        ) > 0
    }

    @Synchronized
    fun getRetentionDays(ownerId: String): Int {
        validateOwner(ownerId)
        readableDatabase.query(
            "user_settings",
            arrayOf("retention_days"),
            "owner_id = ?",
            arrayOf(ownerId),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else DEFAULT_RETENTION_DAYS
        }
    }

    @Synchronized
    fun setRetentionDays(ownerId: String, days: Int) {
        validateOwner(ownerId)
        require(days in ALLOWED_RETENTION_DAYS)
        val db = writableDatabase
        val values = ContentValues().apply { put("retention_days", days) }
        val updated = db.update("user_settings", values, "owner_id = ?", arrayOf(ownerId))
        if (updated == 0) {
            db.insertOrThrow(
                "user_settings",
                null,
                ContentValues().apply {
                    put("owner_id", ownerId)
                    put("retention_days", days)
                    put("memory_context_enabled", 0)
                }
            )
        }
    }

    @Synchronized
    fun isMemoryContextEnabled(ownerId: String): Boolean {
        validateOwner(ownerId)
        readableDatabase.query(
            "user_settings",
            arrayOf("memory_context_enabled"),
            "owner_id = ?",
            arrayOf(ownerId),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            return cursor.moveToFirst() && cursor.getInt(0) == 1
        }
    }

    @Synchronized
    fun setMemoryContextEnabled(ownerId: String, enabled: Boolean) {
        validateOwner(ownerId)
        val db = writableDatabase
        val values = ContentValues().apply { put("memory_context_enabled", if (enabled) 1 else 0) }
        val updated = db.update("user_settings", values, "owner_id = ?", arrayOf(ownerId))
        if (updated == 0) {
            db.insertOrThrow(
                "user_settings",
                null,
                ContentValues().apply {
                    put("owner_id", ownerId)
                    put("retention_days", DEFAULT_RETENTION_DAYS)
                    put("memory_context_enabled", if (enabled) 1 else 0)
                }
            )
        }
    }

    @Synchronized
    fun pruneExpired(ownerId: String, nowMillis: Long = System.currentTimeMillis()): Int {
        validateOwner(ownerId)
        val days = getRetentionDays(ownerId)
        val db = writableDatabase
        db.beginTransaction()
        try {
            var deleted = 0
            if (days > 0) {
                val cutoff = nowMillis - days.toLong() * MILLIS_PER_DAY
                deleted += db.delete(
                    "conversations",
                    "owner_id = ? AND updated_at < ?",
                    arrayOf(ownerId, cutoff.toString())
                )
                deleted += db.delete(
                    "memory_items",
                    "owner_id = ? AND (created_at < ? OR (expires_at IS NOT NULL AND expires_at <= ?))",
                    arrayOf(ownerId, cutoff.toString(), nowMillis.toString())
                )
            } else {
                deleted += db.delete(
                    "memory_items",
                    "owner_id = ? AND expires_at IS NOT NULL AND expires_at <= ?",
                    arrayOf(ownerId, nowMillis.toString())
                )
            }
            db.setTransactionSuccessful()
            return deleted
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun exportAll(ownerId: String): String {
        validateOwner(ownerId)
        val estimatedBytes = readableDatabase.rawQuery(
            """
            SELECT
                COALESCE((SELECT SUM(LENGTH(title_ciphertext)) FROM conversations WHERE owner_id = ?), 0) +
                COALESCE((SELECT SUM(LENGTH(content_ciphertext)) FROM messages WHERE owner_id = ?), 0) +
                COALESCE((SELECT SUM(LENGTH(content_ciphertext)) FROM memory_items WHERE owner_id = ?), 0)
            """.trimIndent(),
            arrayOf(ownerId, ownerId, ownerId)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
        check(estimatedBytes <= MAX_EXPORT_BYTES) {
            "Local export exceeds the 2 MB safety limit. Delete older records and retry."
        }
        val conversationJson = buildJsonArray {
            listConversations(ownerId).forEach { conversation ->
                add(buildJsonObject {
                    put("id", conversation.id)
                    put("title", conversation.title)
                    put("createdAt", conversation.createdAt)
                    put("updatedAt", conversation.updatedAt)
                    put("messages", buildJsonArray {
                        getMessages(ownerId, conversation.id).forEach { message ->
                            add(buildJsonObject {
                                put("id", message.id)
                                put("role", message.role)
                                put("content", message.content)
                                put("createdAt", message.createdAt)
                            })
                        }
                    })
                })
            }
        }
        val memoryJson = buildJsonArray {
            listMemory(ownerId).forEach { memory ->
                add(buildJsonObject {
                    put("id", memory.id)
                    put("content", memory.content)
                    put("createdAt", memory.createdAt)
                    put("updatedAt", memory.updatedAt)
                })
            }
        }
        val export = buildJsonObject {
            put("schemaVersion", DATABASE_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("retentionDays", getRetentionDays(ownerId))
            put("conversations", conversationJson)
            put("memory", memoryJson)
        }.toString()
        check(export.toByteArray(Charsets.UTF_8).size <= MAX_EXPORT_BYTES) {
            "Local export exceeds the 2 MB safety limit. Delete older records and retry."
        }
        return export
    }


    @Synchronized
    fun renameConversation(ownerId: String, conversationId: String, title: String) {
        validateOwner(ownerId)
        require(conversationId.isNotBlank())
        val normalized = title.trim()
        require(normalized.isNotEmpty() && normalized.length <= MAX_TITLE_CHARS)
        val updated = writableDatabase.update(
            "conversations",
            ContentValues().apply {
                put("title_ciphertext", cipher.encrypt(ownerId, normalized))
                put("updated_at", System.currentTimeMillis())
            },
            "id = ? AND owner_id = ?",
            arrayOf(conversationId, ownerId)
        )
        check(updated == 1) { "Conversation does not exist for this account." }
    }

    @Synchronized
    fun exportConversation(ownerId: String, conversationId: String): String {
        validateOwner(ownerId)
        val summary = listConversations(ownerId).firstOrNull { it.id == conversationId }
            ?: throw IllegalArgumentException("Conversation does not exist for this account.")
        val payload = buildJsonObject {
            put("schemaVersion", DATABASE_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("conversation", buildJsonObject {
                put("id", summary.id)
                put("title", summary.title)
                put("createdAt", summary.createdAt)
                put("updatedAt", summary.updatedAt)
                put("messages", buildJsonArray {
                    getMessages(ownerId, conversationId).forEach { message ->
                        add(buildJsonObject {
                            put("role", message.role)
                            put("content", message.content)
                            put("createdAt", message.createdAt)
                        })
                    }
                })
            })
        }.toString()
        check(payload.toByteArray(Charsets.UTF_8).size <= MAX_EXPORT_BYTES)
        return payload
    }

    /**
     * Validates the complete payload before opening the write transaction. The
     * transaction then guarantees all-or-nothing replacement/merge semantics.
     */
    @Synchronized
    fun restoreAll(
        ownerId: String,
        exportJson: String,
        conflictPolicy: RestoreConflictPolicy,
        restoreSettings: Boolean = true
    ): RestoreSummary {
        validateOwner(ownerId)
        require(exportJson.toByteArray(Charsets.UTF_8).size <= MAX_EXPORT_BYTES) {
            "Backup exceeds the local restore size limit."
        }
        val root = kotlinx.serialization.json.Json.parseToJsonElement(exportJson).jsonObject
        val version = root["schemaVersion"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: throw IllegalArgumentException("Backup schema version is missing.")
        require(version in 1..DATABASE_VERSION) { "Backup was created by a newer app version." }
        val retentionDays = root["retentionDays"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: DEFAULT_RETENTION_DAYS
        require(retentionDays in ALLOWED_RETENTION_DAYS)
        val conversations = root["conversations"]?.jsonArray
            ?: throw IllegalArgumentException("Conversation list is missing.")
        val memories = root["memory"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
        require(conversations.size <= MAX_CONVERSATIONS && memories.size <= MAX_MEMORY_ITEMS)

        data class IncomingMessage(val role: String, val content: String, val createdAt: Long)
        data class IncomingConversation(
            val id: String, val title: String, val createdAt: Long, val updatedAt: Long,
            val messages: List<IncomingMessage>
        )
        data class IncomingMemory(val id: String, val content: String, val createdAt: Long, val updatedAt: Long)

        val seenConversationIds = mutableSetOf<String>()
        val incomingConversations = conversations.map { value ->
            val item = value.jsonObject
            val id = item["id"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Conversation ID is missing.")
            require(id.isNotBlank() && id.length <= 128 && seenConversationIds.add(id))
            val title = item["title"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Conversation title is missing.")
            require(title.isNotBlank() && title.length <= MAX_TITLE_CHARS)
            val createdAt = item["createdAt"]?.jsonPrimitive?.content?.toLongOrNull()
                ?: throw IllegalArgumentException("Conversation timestamp is invalid.")
            val updatedAt = item["updatedAt"]?.jsonPrimitive?.content?.toLongOrNull()
                ?: throw IllegalArgumentException("Conversation timestamp is invalid.")
            val messages = item["messages"]?.jsonArray?.map { raw ->
                val message = raw.jsonObject
                val role = message["role"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("Message role is missing.")
                val content = message["content"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("Message content is missing.")
                val timestamp = message["createdAt"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: throw IllegalArgumentException("Message timestamp is invalid.")
                require(role == ROLE_USER || role == ROLE_ASSISTANT)
                require(content.isNotBlank() && content.length <= MAX_MESSAGE_CHARS)
                IncomingMessage(role, content, timestamp)
            } ?: emptyList()
            require(messages.size <= MAX_MESSAGES_PER_CONVERSATION)
            IncomingConversation(id, title, createdAt, updatedAt, messages)
        }
        val seenMemoryIds = mutableSetOf<String>()
        val incomingMemories = memories.map { value ->
            val item = value.jsonObject
            val id = item["id"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Memory ID is missing.")
            val content = item["content"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Memory content is missing.")
            val createdAt = item["createdAt"]?.jsonPrimitive?.content?.toLongOrNull()
                ?: throw IllegalArgumentException("Memory timestamp is invalid.")
            val updatedAt = item["updatedAt"]?.jsonPrimitive?.content?.toLongOrNull()
                ?: createdAt
            require(id.isNotBlank() && id.length <= 128 && seenMemoryIds.add(id))
            require(content.isNotBlank() && content.length <= MAX_MEMORY_CHARS)
            IncomingMemory(id, content, createdAt, updatedAt)
        }

        val db = writableDatabase
        db.beginTransaction()
        try {
            if (conflictPolicy == RestoreConflictPolicy.REPLACE) {
                db.delete("conversations", "owner_id = ?", arrayOf(ownerId))
                db.delete("memory_items", "owner_id = ?", arrayOf(ownerId))
            }
            val existingConversationIds = db.query(
                "conversations", arrayOf("id"), "owner_id = ?", arrayOf(ownerId),
                null, null, null
            ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            val existingMemoryIds = db.query(
                "memory_items", arrayOf("id"), "owner_id = ?", arrayOf(ownerId),
                null, null, null
            ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

            var importedConversations = 0
            var skippedConversations = 0
            var importedMessages = 0
            var importedMemories = 0
            var currentConversationCount = if (conflictPolicy == RestoreConflictPolicy.REPLACE) 0 else existingConversationIds.size
            require(currentConversationCount + incomingConversations.count { it.id !in existingConversationIds } <= MAX_CONVERSATIONS)

            incomingConversations.forEach { conversation ->
                val exists = conversation.id in existingConversationIds
                if (exists && conflictPolicy == RestoreConflictPolicy.KEEP_EXISTING) {
                    skippedConversations++
                    return@forEach
                }
                if (!exists) {
                    db.insertOrThrow("conversations", null, ContentValues().apply {
                        put("id", conversation.id)
                        put("owner_id", ownerId)
                        put("title_ciphertext", cipher.encrypt(ownerId, conversation.title))
                        put("created_at", conversation.createdAt)
                        put("updated_at", conversation.updatedAt)
                    })
                    importedConversations++
                } else if (conflictPolicy == RestoreConflictPolicy.REPLACE) {
                    db.update("conversations", ContentValues().apply {
                        put("title_ciphertext", cipher.encrypt(ownerId, conversation.title))
                        put("created_at", conversation.createdAt)
                        put("updated_at", conversation.updatedAt)
                    }, "id = ? AND owner_id = ?", arrayOf(conversation.id, ownerId))
                }

                val existingMessageKeys = if (exists && conflictPolicy == RestoreConflictPolicy.MERGE) {
                    db.query("messages", arrayOf("role", "content_ciphertext", "created_at"),
                        "owner_id = ? AND conversation_id = ?", arrayOf(ownerId, conversation.id),
                        null, null, null).use { cursor ->
                        buildSet {
                            while (cursor.moveToNext()) {
                                add(cursor.getString(0) + "|" + cipher.decrypt(ownerId, cursor.getString(1)) + "|" + cursor.getLong(2))
                            }
                        }
                    }
                } else emptySet()
                val incomingKeys = mutableSetOf<String>()
                conversation.messages.forEach { message ->
                    val key = message.role + "|" + message.content + "|" + message.createdAt
                    if (key !in existingMessageKeys && incomingKeys.add(key)) {
                        db.insertOrThrow("messages", null, ContentValues().apply {
                            put("conversation_id", conversation.id)
                            put("owner_id", ownerId)
                            put("role", message.role)
                            put("content_ciphertext", cipher.encrypt(ownerId, message.content))
                            put("created_at", message.createdAt)
                        })
                        importedMessages++
                    }
                }
            }
            incomingMemories.forEach { memory ->
                if (memory.id in existingMemoryIds && conflictPolicy != RestoreConflictPolicy.REPLACE) return@forEach
                db.insertOrThrow("memory_items", null, ContentValues().apply {
                    put("id", memory.id)
                    put("owner_id", ownerId)
                    put("content_ciphertext", cipher.encrypt(ownerId, memory.content))
                    put("created_at", memory.createdAt)
                    put("updated_at", memory.updatedAt)
                })
                importedMemories++
            }
            if (conflictPolicy == RestoreConflictPolicy.REPLACE && restoreSettings) {
                db.insertWithOnConflict("user_settings", null, ContentValues().apply {
                    put("owner_id", ownerId)
                    put("retention_days", retentionDays)
                    put("memory_context_enabled", 0)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
            return RestoreSummary(importedConversations, skippedConversations, importedMessages, importedMemories)
        } finally {
            db.endTransaction()
        }
    }

    private fun validateOwner(ownerId: String) {
        require(ownerId.isNotBlank() && ownerId.length <= MAX_OWNER_ID_CHARS)
    }

    companion object {
        const val DATABASE_NAME = "miss_minutes_local_memory.db"
        const val DATABASE_VERSION = 2
        const val DEFAULT_RETENTION_DAYS = 90
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val MAX_MESSAGE_CHARS = 8_000
        const val MAX_MEMORY_CHARS = 2_000
        const val MAX_EXPORT_BYTES = 2_000_000
        const val MAX_TITLE_CHARS = 96
        const val MAX_CONVERSATIONS = 1_000
        const val MAX_MESSAGES_PER_CONVERSATION = 500
        const val MAX_MEMORY_ITEMS = 1_000
        const val MILLIS_PER_DAY = 86_400_000L
        val ALLOWED_RETENTION_DAYS = setOf(0, 30, 90, 365)
        private const val MAX_OWNER_ID_CHARS = 128
        private const val DEFAULT_TITLE = "New conversation"
    }
}

internal object ConversationSchema {
    fun createVersionOne(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE conversations (
                id TEXT PRIMARY KEY NOT NULL,
                owner_id TEXT NOT NULL,
                title_ciphertext TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_id TEXT NOT NULL,
                owner_id TEXT NOT NULL,
                role TEXT NOT NULL CHECK(role IN ('user', 'assistant')),
                content_ciphertext TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_conversations_owner_updated ON conversations(owner_id, updated_at DESC)")
        db.execSQL("CREATE INDEX idx_messages_conversation_order ON messages(owner_id, conversation_id, id ASC)")
    }

    fun upgradeToVersionTwo(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE memory_items (
                id TEXT PRIMARY KEY NOT NULL,
                owner_id TEXT NOT NULL,
                content_ciphertext TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                expires_at INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_memory_owner_updated ON memory_items(owner_id, updated_at DESC)")
        db.execSQL(
            """
            CREATE TABLE user_settings (
                owner_id TEXT PRIMARY KEY NOT NULL,
                retention_days INTEGER NOT NULL DEFAULT 90 CHECK(retention_days IN (0, 30, 90, 365)),
                memory_context_enabled INTEGER NOT NULL DEFAULT 0 CHECK(memory_context_enabled IN (0, 1))
            )
            """.trimIndent()
        )
    }
}
