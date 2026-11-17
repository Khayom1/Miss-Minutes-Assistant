package com.khayom1.missminutes.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalConversationStoreTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun newStore(): Pair<String, LocalConversationStore> {
        val name = "memory-test-${UUID.randomUUID()}.db"
        return name to LocalConversationStore(
            context = context,
            cipher = AndroidKeystoreConversationCipher("miss_minutes_test_conversation_aes_v1"),
            databaseName = name
        )
    }

    private fun clean(name: String, store: LocalConversationStore) {
        store.close()
        context.deleteDatabase(name)
    }

    @Test
    fun messagesAndMemoryPersistAcrossStoreInstancesAndRemainAccountScoped() {
        val (name, firstStore) = newStore()
        var conversationId = ""
        var memoryId = ""
        try {
            conversationId = firstStore.createConversation("user-a", nowMillis = 1_000L)
            firstStore.appendMessage("user-a", conversationId, "user", "private message alpha", 2_000L)
            firstStore.appendMessage("user-a", conversationId, "assistant", "private reply beta", 3_000L)
            memoryId = firstStore.addMemory("user-a", "private memory gamma", 4_000L)
        } finally {
            firstStore.close()
        }

        val reopened = LocalConversationStore(
            context,
            AndroidKeystoreConversationCipher("miss_minutes_test_conversation_aes_v1"),
            name
        )
        try {
            assertEquals("private message alpha", reopened.getMessages("user-a", conversationId)[0].content)
            assertEquals(2, reopened.getMessages("user-a", conversationId).size)
            assertEquals("private memory gamma", reopened.listMemory("user-a").single().content)
            assertTrue(reopened.listConversations("user-b").isEmpty())
            assertTrue(reopened.getMessages("user-b", conversationId).isEmpty())
            assertFalse(reopened.deleteConversation("user-b", conversationId))
            assertFalse(reopened.deleteMemory("user-b", memoryId))
        } finally {
            clean(name, reopened)
        }
    }

    @Test
    fun databaseFileDoesNotContainPlaintextConversationOrMemoryContent() {
        val (name, store) = newStore()
        val marker = "PLAINTEXT_SHOULD_NEVER_APPEAR_7f92a"
        try {
            val id = store.createConversation("user-private")
            store.appendMessage("user-private", id, "user", marker)
            store.addMemory("user-private", marker + "_MEMORY")
            store.close()

            val bytes = context.getDatabasePath(name).readBytes()
            val raw = bytes.toString(Charsets.ISO_8859_1)
            assertFalse(raw.contains(marker))
            assertFalse(raw.contains(marker + "_MEMORY"))
        } finally {
            store.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun authenticatedEncryptionRejectsCiphertextFromAnotherOwner() {
        val cipher = AndroidKeystoreConversationCipher("miss_minutes_test_conversation_aes_v1")
        val encrypted = cipher.encrypt("owner-a", "owner-private-value")
        val error = runCatching { cipher.decrypt("owner-b", encrypted) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
    }

    @Test
    fun exportContainsOnlyDecryptedDataForTheRequestedOwner() {
        val (name, store) = newStore()
        try {
            val id = store.createConversation("owner-one")
            store.appendMessage("owner-one", id, "user", "export me")
            store.addMemory("owner-one", "remember this")
            val export = store.exportAll("owner-one")

            assertTrue(export.contains("\"schemaVersion\":2"))
            assertTrue(export.contains("export me"))
            assertTrue(export.contains("remember this"))
            assertFalse(store.exportAll("owner-two").contains("export me"))
        } finally {
            clean(name, store)
        }
    }

    @Test
    fun retentionDeletesExpiredRecordsAndCanBeDisabled() {
        val (name, store) = newStore()
        try {
            val now = 2_000_000_000_000L
            val old = now - 31L * LocalConversationStore.MILLIS_PER_DAY
            val expiredConversation = store.createConversation("owner", nowMillis = old)
            store.appendMessage("owner", expiredConversation, "user", "old conversation", old)
            store.addMemory("owner", "old memory", old)
            val currentConversation = store.createConversation("owner", nowMillis = now)

            store.setRetentionDays("owner", 30)
            assertEquals(90, LocalConversationStore.DEFAULT_RETENTION_DAYS)
            assertTrue(store.pruneExpired("owner", now) >= 2)
            assertTrue(store.listConversations("owner").none { it.id == expiredConversation })
            assertTrue(store.listMemory("owner").isEmpty())
            assertTrue(store.listConversations("owner").any { it.id == currentConversation })

            store.setRetentionDays("owner", 0)
            assertEquals(0, store.getRetentionDays("owner"))
            val retained = store.createConversation("owner", nowMillis = old)
            store.appendMessage("owner", retained, "user", "keep forever", old)
            store.pruneExpired("owner", now + 500L * LocalConversationStore.MILLIS_PER_DAY)
            assertTrue(store.listConversations("owner").any { it.id == retained })
        } finally {
            clean(name, store)
        }
    }

    @Test
    fun reachingMessageLimitNeverSilentlyDeletesEarlierHistory() {
        val (name, store) = newStore()
        try {
            val id = store.createConversation("owner")
            repeat(LocalConversationStore.MAX_MESSAGES_PER_CONVERSATION) { index ->
                store.appendMessage("owner", id, "user", "message-$index")
            }
            val error = runCatching {
                store.appendMessage("owner", id, "user", "message-over-limit")
            }.exceptionOrNull()

            assertTrue(error is ConversationLimitReachedException)
            val messages = store.getMessages("owner", id)
            assertEquals(LocalConversationStore.MAX_MESSAGES_PER_CONVERSATION, messages.size)
            assertEquals("message-0", messages.first().content)
            assertEquals("message-${LocalConversationStore.MAX_MESSAGES_PER_CONVERSATION - 1}", messages.last().content)
        } finally {
            clean(name, store)
        }
    }

    @Test
    fun savedMemoryIsNotSentToAiUntilExplicitlyEnabledAndPreferencePersists() {
        val (name, store) = newStore()
        try {
            assertFalse(store.isMemoryContextEnabled("owner"))
            store.setMemoryContextEnabled("owner", true)
            assertTrue(store.isMemoryContextEnabled("owner"))
            store.setRetentionDays("owner", 365)
            assertTrue(store.isMemoryContextEnabled("owner"))
            assertFalse(store.isMemoryContextEnabled("another-owner"))
            store.setMemoryContextEnabled("owner", false)
            assertFalse(store.isMemoryContextEnabled("owner"))
        } finally {
            clean(name, store)
        }
    }

    @Test
    fun deleteAllRemovesConversationsAndMemoryButKeepsRetentionPreference() {
        val (name, store) = newStore()
        try {
            val id = store.createConversation("owner")
            store.appendMessage("owner", id, "user", "delete me")
            store.addMemory("owner", "delete memory")
            store.setRetentionDays("owner", 365)

            assertEquals(2, store.deleteAll("owner"))
            assertTrue(store.listConversations("owner").isEmpty())
            assertTrue(store.listMemory("owner").isEmpty())
            assertEquals(365, store.getRetentionDays("owner"))
        } finally {
            clean(name, store)
        }
    }

    @Test
    fun replaceRestoreRollsBackEntireTransactionWhenEncryptionFails() {
        val name = "restore-rollback-${UUID.randomUUID()}.db"
        val cipher = FailingConversationCipher()
        val store = LocalConversationStore(context, cipher, name)
        try {
            val existingId = store.createConversation("owner", title = "Preserve this chat")
            store.appendMessage("owner", existingId, "user", "preserve this message")
            store.addMemory("owner", "preserve this memory")
            store.setRetentionDays("owner", 30)
            val callsBeforeRestore = cipher.encryptCalls
            cipher.failOnEncryptCall = callsBeforeRestore + 2

            val incoming = """
                {"schemaVersion":2,"exportedAt":1000,"retentionDays":90,
                 "conversations":[{"id":"incoming-id","title":"incoming","createdAt":1,"updatedAt":2,
                 "messages":[{"role":"user","content":"incoming message","createdAt":2}]}],
                 "memory":[]}
            """.trimIndent()
            val error = runCatching {
                store.restoreAll("owner", incoming, RestoreConflictPolicy.REPLACE)
            }.exceptionOrNull()

            assertTrue(error is IllegalStateException)
            assertEquals(listOf("Preserve this chat"), store.listConversations("owner").map { it.title })
            assertEquals("preserve this message", store.getMessages("owner", existingId).single().content)
            assertEquals("preserve this memory", store.listMemory("owner").single().content)
            assertEquals(30, store.getRetentionDays("owner"))
        } finally {
            clean(name, store)
        }
    }

    @Test
    fun versionOneDatabaseMigratesWithoutLosingEncryptedMessages() {
        val name = "memory-migration-${UUID.randomUUID()}.db"
        val cipher = AndroidKeystoreConversationCipher("miss_minutes_test_conversation_aes_v1")
        val owner = "migration-owner"
        val conversationId = UUID.randomUUID().toString()
        val legacy = LegacyVersionOneDatabase(context, name)
        try {
            val db = legacy.writableDatabase
            db.insertOrThrow(
                "conversations",
                null,
                ContentValues().apply {
                    put("id", conversationId)
                    put("owner_id", owner)
                    put("title_ciphertext", cipher.encrypt(owner, "Migrated conversation"))
                    put("created_at", 10L)
                    put("updated_at", 20L)
                }
            )
            db.insertOrThrow(
                "messages",
                null,
                ContentValues().apply {
                    put("conversation_id", conversationId)
                    put("owner_id", owner)
                    put("role", "user")
                    put("content_ciphertext", cipher.encrypt(owner, "Migrated message"))
                    put("created_at", 20L)
                }
            )
        } finally {
            legacy.close()
        }

        val migrated = LocalConversationStore(context, cipher, name)
        try {
            assertEquals(LocalConversationStore.DATABASE_VERSION, migrated.readableDatabase.version)
            assertEquals("Migrated conversation", migrated.listConversations(owner).single().title)
            assertEquals("Migrated message", migrated.getMessages(owner, conversationId).single().content)
            migrated.addMemory(owner, "Memory table created by migration")
            assertEquals("Memory table created by migration", migrated.listMemory(owner).single().content)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    private class FailingConversationCipher : ConversationCipher {
        private val delegate = AndroidKeystoreConversationCipher("miss_minutes_test_conversation_aes_v1")
        var encryptCalls = 0
        var failOnEncryptCall: Int? = null

        override fun encrypt(ownerId: String, plaintext: String): String {
            encryptCalls++
            if (encryptCalls == failOnEncryptCall) {
                throw IllegalStateException("Forced encryption failure for rollback test.")
            }
            return delegate.encrypt(ownerId, plaintext)
        }

        override fun decrypt(ownerId: String, ciphertext: String): String =
            delegate.decrypt(ownerId, ciphertext)
    }

    private class LegacyVersionOneDatabase(
        context: Context,
        name: String
    ) : SQLiteOpenHelper(context, name, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            ConversationSchema.createVersionOne(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
