package com.khayom1.missminutes.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

enum class ConversationRole { USER, ASSISTANT }

data class StoredConversation(
    val id: String,
    val title: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val archived: Boolean
)

data class StoredMessage(
    val id: String,
    val conversationId: String,
    val role: ConversationRole,
    val content: String,
    val createdAtEpochMillis: Long
)

data class StoredMemory(
    val id: String,
    val key: String,
    val value: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

/**
 * Offline-first conversation history. All user-authored and assistant message
 * text plus conversation titles are encrypted with AES-256-GCM before SQLite.
 * Owner IDs are opaque Supabase user IDs and are never rendered in UI.
 */
class AndroidConversationStore(
    context: Context,
    databaseName: String = DATABASE_NAME,
    private val clock: () -> Long = System::currentTimeMillis,
    private val keyProvider: () -> SecretKey = ::getOrCreateKey
) : AutoCloseable {
    private val helper = ConversationDatabase(context.applicationContext, databaseName)

    @Synchronized
    fun createConversation(ownerId: String, title: String = DEFAULT_TITLE): StoredConversation {
        require(ownerId.isNotBlank())
        require(title.isNotBlank() && title.length <= MAX_TITLE_CHARS)
        val now = clock()
        val id = UUID.randomUUID().toString()
        val encryptedTitle = encrypt(title, aad(ownerId, id, TITLE_FIELD))
        val values = ContentValues().apply {
            put(COL_ID, id)
            put(COL_OWNER, ownerId)
            put(COL_TITLE, encryptedTitle)
            put(COL_CREATED, now)
            put(COL_UPDATED, now)
            put(COL_ARCHIVED, 0)
        }
        helper.writableDatabase.insertOrThrow(TABLE_CONVERSATIONS, null, values)
        return StoredConversation(id, title, now, now, false)
    }

    @Synchronized
    fun appendMessage(
        ownerId: String,
        conversationId: String,
        role: ConversationRole,
        content: String
    ): StoredMessage {
        require(ownerId.isNotBlank() && conversationId.isNotBlank())
        require(content.isNotBlank() && content.length <= MAX_MESSAGE_CHARS)
        val db = helper.writableDatabase
        val now = clock()
        val messageId = UUID.randomUUID().toString()
        val encrypted = encrypt(content, aad(ownerId, messageId, MESSAGE_FIELD))
        db.beginTransaction()
        try {
            val ownerExists = db.query(
                TABLE_CONVERSATIONS,
                arrayOf(COL_ID),
                "$COL_ID = ? AND $COL_OWNER = ?",
                arrayOf(conversationId, ownerId),
                null, null, null, "1"
            ).use { it.moveToFirst() }
            check(ownerExists) { "Conversation does not belong to this account." }

            val values = ContentValues().apply {
                put(COL_ID, messageId)
                put(COL_CONVERSATION_ID, conversationId)
                put(COL_ROLE, role.name)
                put(COL_CONTENT, encrypted)
                put(COL_CREATED, now)
            }
            db.insertOrThrow(TABLE_MESSAGES, null, values)

            if (role == ConversationRole.USER) {
                val count = db.compileStatement(
                    "SELECT COUNT(*) FROM $TABLE_MESSAGES WHERE $COL_CONVERSATION_ID = ? AND $COL_ROLE = 'USER'"
                ).run { bindString(1, conversationId); simpleQueryForLong() }
                if (count == 1L) {
                    val title = content.trim().replace(Regex("\\s+"), " ").take(MAX_TITLE_CHARS)
                    val titleCipher = encrypt(title, aad(ownerId, conversationId, TITLE_FIELD))
                    val update = ContentValues().apply {
                        put(COL_TITLE, titleCipher)
                        put(COL_UPDATED, now)
                    }
                    db.update(TABLE_CONVERSATIONS, update, "$COL_ID = ? AND $COL_OWNER = ?", arrayOf(conversationId, ownerId))
                } else {
                    val update = ContentValues().apply { put(COL_UPDATED, now) }
                    db.update(TABLE_CONVERSATIONS, update, "$COL_ID = ? AND $COL_OWNER = ?", arrayOf(conversationId, ownerId))
                }
            } else {
                val update = ContentValues().apply { put(COL_UPDATED, now) }
                db.update(TABLE_CONVERSATIONS, update, "$COL_ID = ? AND $COL_OWNER = ?", arrayOf(conversationId, ownerId))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return StoredMessage(messageId, conversationId, role, content, now)
    }

    @Synchronized
    fun listConversations(ownerId: String, includeArchived: Boolean = false): List<StoredConversation> {
        require(ownerId.isNotBlank())
        val selection = if (includeArchived) "$COL_OWNER = ?" else "$COL_OWNER = ? AND $COL_ARCHIVED = 0"
        return helper.readableDatabase.query(
            TABLE_CONVERSATIONS,
            arrayOf(COL_ID, COL_TITLE, COL_CREATED, COL_UPDATED, COL_ARCHIVED),
            selection,
            arrayOf(ownerId),
            null, null, "$COL_UPDATED DESC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    add(
                        StoredConversation(
                            id = id,
                            title = decrypt(cursor.getBlob(1), aad(ownerId, id, TITLE_FIELD)),
                            createdAtEpochMillis = cursor.getLong(2),
                            updatedAtEpochMillis = cursor.getLong(3),
                            archived = cursor.getInt(4) != 0
                        )
                    )
                }
            }
        }
    }

    @Synchronized
    fun loadMessages(ownerId: String, conversationId: String): List<StoredMessage> {
        require(ownerId.isNotBlank() && conversationId.isNotBlank())
        val db = helper.readableDatabase
        val ownsConversation = db.query(
            TABLE_CONVERSATIONS, arrayOf(COL_ID),
            "$COL_ID = ? AND $COL_OWNER = ?", arrayOf(conversationId, ownerId),
            null, null, null, "1"
        ).use { it.moveToFirst() }
        if (!ownsConversation) return emptyList()

        return db.query(
            TABLE_MESSAGES,
            arrayOf(COL_ID, COL_ROLE, COL_CONTENT, COL_CREATED),
            "$COL_CONVERSATION_ID = ?",
            arrayOf(conversationId),
            null, null, "$COL_CREATED ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val role = runCatching { ConversationRole.valueOf(cursor.getString(1)) }.getOrNull()
                        ?: continue
                    add(
                        StoredMessage(
                            id = id,
                            conversationId = conversationId,
                            role = role,
                            content = decrypt(cursor.getBlob(2), aad(ownerId, id, MESSAGE_FIELD)),
                            createdAtEpochMillis = cursor.getLong(3)
                        )
                    )
                }
            }
        }
    }

    @Synchronized
    fun saveMemory(ownerId: String, key: String, value: String): StoredMemory {
        require(ownerId.isNotBlank())
        val normalizedKey = key.trim()
        require(normalizedKey.isNotEmpty() && normalizedKey.length <= MAX_MEMORY_KEY_CHARS)
        require(value.isNotBlank() && value.length <= MAX_MEMORY_VALUE_CHARS)
        val db = helper.writableDatabase
        val existingId = db.query(
            TABLE_MEMORIES, arrayOf(COL_ID),
            "$COL_OWNER = ?", arrayOf(ownerId), null, null, null
        ).use { cursor ->
            var found: String? = null
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val keyCipher = db.query(
                    TABLE_MEMORIES, arrayOf(COL_MEMORY_KEY), "$COL_ID = ?",
                    arrayOf(id), null, null, null, "1"
                ).use { inner -> if (inner.moveToFirst()) inner.getBlob(0) else byteArrayOf() }
                val storedKey = decrypt(keyCipher, aad(ownerId, id, MEMORY_KEY_FIELD))
                if (storedKey.equals(normalizedKey, ignoreCase = true)) {
                    found = id
                    break
                }
            }
            found
        }
        val now = clock()
        val id = existingId ?: UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put(COL_ID, id)
            put(COL_OWNER, ownerId)
            put(COL_MEMORY_KEY, encrypt(normalizedKey, aad(ownerId, id, MEMORY_KEY_FIELD)))
            put(COL_MEMORY_VALUE, encrypt(value.trim(), aad(ownerId, id, MEMORY_VALUE_FIELD)))
            if (existingId == null) put(COL_CREATED, now)
            put(COL_UPDATED, now)
        }
        db.insertWithOnConflict(TABLE_MEMORIES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        val created = db.query(
            TABLE_MEMORIES, arrayOf(COL_CREATED), "$COL_ID = ?", arrayOf(id),
            null, null, null, "1"
        ).use { if (it.moveToFirst()) it.getLong(0) else now }
        return StoredMemory(id, normalizedKey, value.trim(), created, now)
    }

    @Synchronized
    fun listMemories(ownerId: String): List<StoredMemory> {
        require(ownerId.isNotBlank())
        return helper.readableDatabase.query(
            TABLE_MEMORIES,
            arrayOf(COL_ID, COL_MEMORY_KEY, COL_MEMORY_VALUE, COL_CREATED, COL_UPDATED),
            "$COL_OWNER = ?", arrayOf(ownerId), null, null, "$COL_UPDATED DESC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    add(
                        StoredMemory(
                            id = id,
                            key = decrypt(cursor.getBlob(1), aad(ownerId, id, MEMORY_KEY_FIELD)),
                            value = decrypt(cursor.getBlob(2), aad(ownerId, id, MEMORY_VALUE_FIELD)),
                            createdAtEpochMillis = cursor.getLong(3),
                            updatedAtEpochMillis = cursor.getLong(4)
                        )
                    )
                }
            }
        }
    }

    @Synchronized
    fun deleteMemory(ownerId: String, memoryId: String): Boolean =
        helper.writableDatabase.delete(
            TABLE_MEMORIES, "$COL_ID = ? AND $COL_OWNER = ?", arrayOf(memoryId, ownerId)
        ) > 0

    @Synchronized
    fun clearMemories(ownerId: String): Int =
        helper.writableDatabase.delete(TABLE_MEMORIES, "$COL_OWNER = ?", arrayOf(ownerId))

    @Synchronized
    fun setArchived(ownerId: String, conversationId: String, archived: Boolean) {
        val values = ContentValues().apply { put(COL_ARCHIVED, if (archived) 1 else 0) }
        helper.writableDatabase.update(
            TABLE_CONVERSATIONS, values,
            "$COL_ID = ? AND $COL_OWNER = ?",
            arrayOf(conversationId, ownerId)
        )
    }

    @Synchronized
    fun deleteConversation(ownerId: String, conversationId: String): Boolean {
        return helper.writableDatabase.delete(
            TABLE_CONVERSATIONS, "$COL_ID = ? AND $COL_OWNER = ?",
            arrayOf(conversationId, ownerId)
        ) > 0
    }

    @Synchronized
    fun deleteAll(ownerId: String): Int {
        require(ownerId.isNotBlank())
        return helper.writableDatabase.delete(TABLE_CONVERSATIONS, "$COL_OWNER = ?", arrayOf(ownerId))
    }

    @Synchronized
    fun deleteOlderThan(ownerId: String, cutoffEpochMillis: Long): Int {
        require(ownerId.isNotBlank() && cutoffEpochMillis >= 0)
        return helper.writableDatabase.delete(
            TABLE_CONVERSATIONS, "$COL_OWNER = ? AND $COL_UPDATED < ?",
            arrayOf(ownerId, cutoffEpochMillis.toString())
        )
    }

    /** Returns plaintext only to an explicit caller for a user-initiated export. */
    @Synchronized
    fun exportJson(ownerId: String): String {
        val conversations = JSONArray()
        listConversations(ownerId, includeArchived = true).forEach { conversation ->
            val item = JSONObject()
                .put("id", conversation.id)
                .put("title", conversation.title)
                .put("created_at", conversation.createdAtEpochMillis)
                .put("updated_at", conversation.updatedAtEpochMillis)
                .put("archived", conversation.archived)
            val messages = JSONArray()
            loadMessages(ownerId, conversation.id).forEach { message ->
                messages.put(
                    JSONObject()
                        .put("role", message.role.name.lowercase())
                        .put("content", message.content)
                        .put("created_at", message.createdAtEpochMillis)
                )
            }
            item.put("messages", messages)
            conversations.put(item)
        }
        return JSONObject()
            .put("format", EXPORT_FORMAT)
            .put("schema_version", DATABASE_VERSION)
            .put("exported_at", clock())
            .put("conversations", conversations)
            .put("memories", JSONArray().also { array ->
                listMemories(ownerId).forEach { memory ->
                    array.put(JSONObject().put("key", memory.key).put("value", memory.value))
                }
            })
            .toString()
    }

    override fun close() = helper.close()

    private fun aad(ownerId: String, recordId: String, field: String) =
        "$AAD_PREFIX|$ownerId|$recordId|$field".toByteArray(Charsets.UTF_8)

    private fun encrypt(plainText: String, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        cipher.updateAAD(associatedData)
        return cipher.iv + cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
    }

    private fun decrypt(payload: ByteArray, associatedData: ByteArray): String {
        require(payload.size > IV_BYTES + TAG_BYTES) { "Stored conversation payload is invalid." }
        val iv = payload.copyOfRange(0, IV_BYTES)
        val ciphertext = payload.copyOfRange(IV_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private class ConversationDatabase(context: Context, name: String) :
        SQLiteOpenHelper(context, name, null, DATABASE_VERSION) {
        override fun onConfigure(db: SQLiteDatabase) {
            super.onConfigure(db)
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            createV1(db)
            db.execSQL("ALTER TABLE $TABLE_CONVERSATIONS ADD COLUMN $COL_ARCHIVED INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX conversations_owner_updated ON $TABLE_CONVERSATIONS($COL_OWNER, $COL_UPDATED DESC)")
            db.execSQL("CREATE INDEX messages_conversation_created ON $TABLE_MESSAGES($COL_CONVERSATION_ID, $COL_CREATED)")
            createV3(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            var version = oldVersion
            if (version < 1) {
                createV1(db)
                version = 1
            }
            if (version < 2) {
                db.execSQL("ALTER TABLE $TABLE_CONVERSATIONS ADD COLUMN $COL_ARCHIVED INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX conversations_owner_updated ON $TABLE_CONVERSATIONS($COL_OWNER, $COL_UPDATED DESC)")
                db.execSQL("CREATE INDEX messages_conversation_created ON $TABLE_MESSAGES($COL_CONVERSATION_ID, $COL_CREATED)")
                version = 2
            }
            if (version < 3) {
                createV3(db)
                version = 3
            }
            check(version == newVersion) { "Unsupported conversation database migration." }
        }

        private fun createV3(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $TABLE_MEMORIES (" +
                    "$COL_ID TEXT PRIMARY KEY NOT NULL, " +
                    "$COL_OWNER TEXT NOT NULL, " +
                    "$COL_MEMORY_KEY BLOB NOT NULL, " +
                    "$COL_MEMORY_VALUE BLOB NOT NULL, " +
                    "$COL_CREATED INTEGER NOT NULL, " +
                    "$COL_UPDATED INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX memories_owner_updated ON $TABLE_MEMORIES($COL_OWNER, $COL_UPDATED DESC)")
        }

        private fun createV1(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $TABLE_CONVERSATIONS (" +
                    "$COL_ID TEXT PRIMARY KEY NOT NULL, " +
                    "$COL_OWNER TEXT NOT NULL, " +
                    "$COL_TITLE BLOB NOT NULL, " +
                    "$COL_CREATED INTEGER NOT NULL, " +
                    "$COL_UPDATED INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE $TABLE_MESSAGES (" +
                    "$COL_ID TEXT PRIMARY KEY NOT NULL, " +
                    "$COL_CONVERSATION_ID TEXT NOT NULL REFERENCES $TABLE_CONVERSATIONS($COL_ID) ON DELETE CASCADE, " +
                    "$COL_ROLE TEXT NOT NULL CHECK($COL_ROLE IN ('USER','ASSISTANT')), " +
                    "$COL_CONTENT BLOB NOT NULL, " +
                    "$COL_CREATED INTEGER NOT NULL)"
            )
        }
    }

    companion object {
        const val DATABASE_NAME = "miss_minutes_conversations.db"
        const val DATABASE_VERSION = 3
        const val EXPORT_FORMAT = "miss-minutes-conversations"
        const val DEFAULT_TITLE = "New conversation"
        const val MAX_MESSAGE_CHARS = 16_000
        const val MAX_TITLE_CHARS = 72
        const val MAX_MEMORY_KEY_CHARS = 80
        const val MAX_MEMORY_VALUE_CHARS = 2_000
        private const val MAX_MESSAGE_CHARS_UNUSED = 16_000
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALIAS = "miss_minutes_conversation_aes_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val TAG_BYTES = TAG_BITS / 8
        private const val TITLE_FIELD = "title"
        private const val MESSAGE_FIELD = "message"
        private const val MEMORY_KEY_FIELD = "memory-key"
        private const val MEMORY_VALUE_FIELD = "memory-value"
        private const val AAD_PREFIX = "com.khayom1.missminutes.conversation.v1"

        private const val TABLE_CONVERSATIONS = "conversations"
        private const val TABLE_MESSAGES = "messages"
        private const val TABLE_MEMORIES = "memories"
        private const val COL_ID = "id"
        private const val COL_OWNER = "owner_id"
        private const val COL_TITLE = "title_cipher"
        private const val COL_CREATED = "created_at"
        private const val COL_UPDATED = "updated_at"
        private const val COL_ARCHIVED = "archived"
        private const val COL_CONVERSATION_ID = "conversation_id"
        private const val COL_ROLE = "role"
        private const val COL_CONTENT = "content_cipher"
        private const val COL_MEMORY_KEY = "key_cipher"
        private const val COL_MEMORY_VALUE = "value_cipher"

        private fun getOrCreateKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            return generator.generateKey()
        }
    }
}
