package com.khayom1.missminutes.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
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

/**
 * Offline-first local store. Message and memory content is encrypted with
 * Android Keystore AES-256-GCM before insertion; only ciphertext is stored in
 * SQLite. All reads and writes are explicitly scoped to the authenticated
 * Supabase user id.
 */
class LocalConversationStore(
    context: Context,
    private val cipher: ConversationCipher = AndroidKeystoreConversationCipher(),
    databaseName: String = DATABASE_NAME
) : SQLiteOpenHelper(context.applicationContext, databaseName, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
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

            val excess = (db.rawQuery(
                "SELECT COUNT(*) FROM messages WHERE conversation_id = ? AND owner_id = ?",
                arrayOf(conversationId, ownerId)
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }) - MAX_MESSAGES_PER_CONVERSATION
            if (excess > 0) {
                db.execSQL(
                    "DELETE FROM messages WHERE id IN (SELECT id FROM messages WHERE conversation_id = ? AND owner_id = ? ORDER BY id ASC LIMIT ?)",
                    arrayOf(conversationId, ownerId, excess)
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
        return buildJsonObject {
            put("schemaVersion", DATABASE_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("retentionDays", getRetentionDays(ownerId))
            put("conversations", conversationJson)
            put("memory", memoryJson)
        }.toString()
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
