package com.khayom1.missminutes.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidConversationStoreTest {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private lateinit var store: AndroidConversationStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "conversation-test-${UUID.randomUUID()}.db"
        store = AndroidConversationStore(context, databaseName)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun messagesAndTitlesRoundTripButDatabaseContainsCiphertextOnly() {
        val conversation = store.createConversation("user-a")
        val userText = "My private dental appointment is tomorrow."
        store.appendMessage("user-a", conversation.id, ConversationRole.USER, userText)
        store.appendMessage("user-a", conversation.id, ConversationRole.ASSISTANT, "I can help you prepare.")

        val restored = store.loadMessages("user-a", conversation.id)
        assertEquals(2, restored.size)
        assertEquals(userText, restored.first().content)
        assertEquals(ConversationRole.ASSISTANT, restored.last().role)
        assertEquals(userText, store.listConversations("user-a").single().title)

        val raw = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        try {
            raw.rawQuery("SELECT hex(title_cipher) FROM conversations", null).use {
                assertTrue(it.moveToFirst())
                assertFalse(it.getString(0).contains(userText.encodeToHex()))
            }
            raw.rawQuery("SELECT hex(content_cipher) FROM messages", null).use {
                assertTrue(it.moveToFirst())
                assertFalse(it.getString(0).contains(userText.encodeToHex()))
            }
        } finally {
            raw.close()
        }
    }

    @Test
    fun accountIsolationPreventsCrossAccountReadsAndWrites() {
        val conversation = store.createConversation("owner-one")
        store.appendMessage("owner-one", conversation.id, ConversationRole.USER, "private")
        assertTrue(store.listConversations("owner-two").isEmpty())
        assertTrue(store.loadMessages("owner-two", conversation.id).isEmpty())

        val error = runCatching {
            store.appendMessage("owner-two", conversation.id, ConversationRole.USER, "intrusion")
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals(1, store.loadMessages("owner-one", conversation.id).size)
    }

    @Test
    fun deleteConversationCascadesAndDeleteAllIsOwnerScoped() {
        val first = store.createConversation("owner-one")
        val second = store.createConversation("owner-one")
        val other = store.createConversation("owner-two")
        store.appendMessage("owner-one", first.id, ConversationRole.USER, "one")
        store.appendMessage("owner-one", second.id, ConversationRole.USER, "two")
        store.appendMessage("owner-two", other.id, ConversationRole.USER, "other")

        assertTrue(store.deleteConversation("owner-one", first.id))
        assertTrue(store.loadMessages("owner-one", first.id).isEmpty())
        assertEquals(1, store.deleteAll("owner-one"))
        assertTrue(store.listConversations("owner-one").isEmpty())
        assertEquals(1, store.listConversations("owner-two").size)
    }

    @Test
    fun archiveRetentionAndExportAreExplicitAndScoped() {
        val old = store.createConversation("owner-one")
        store.appendMessage("owner-one", old.id, ConversationRole.USER, "export me")
        store.setArchived("owner-one", old.id, true)
        assertTrue(store.listConversations("owner-one").isEmpty())
        assertEquals(1, store.listConversations("owner-one", includeArchived = true).size)

        val export = store.exportJson("owner-one")
        assertTrue(export.contains(AndroidConversationStore.EXPORT_FORMAT))
        assertTrue(export.contains("export me"))
        assertFalse(store.exportJson("owner-two").contains("export me"))

        assertEquals(1, store.deleteOlderThan("owner-one", System.currentTimeMillis() + 1))
        assertTrue(store.listConversations("owner-one", includeArchived = true).isEmpty())
    }

    @Test
    fun userControlledMemoriesAreEncryptedScopedAndIndividuallyDeletable() {
        val first = store.saveMemory("owner-one", "Preferred language", "Tajik")
        store.saveMemory("owner-one", "Study goal", "Become a dentist")
        store.saveMemory("owner-two", "Preferred language", "Russian")

        val updated = store.saveMemory("owner-one", "preferred LANGUAGE", "Russian")
        assertEquals(first.id, updated.id)
        assertEquals(2, store.listMemories("owner-one").size)
        assertEquals("Russian", store.listMemories("owner-one").first { it.id == first.id }.value)
        assertEquals(1, store.listMemories("owner-two").size)
        assertTrue(store.deleteMemory("owner-one", first.id))
        assertEquals(1, store.listMemories("owner-one").size)
        assertEquals(1, store.clearMemories("owner-one"))
        assertTrue(store.listMemories("owner-one").isEmpty())
        assertEquals(1, store.listMemories("owner-two").size)
    }

    @Test
    fun memoryPlaintextIsNotPresentInSqliteRows() {
        store.saveMemory("owner-one", "private-key-phrase", "private-value-phrase")
        val raw = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        try {
            raw.rawQuery("SELECT hex(key_cipher), hex(value_cipher) FROM memories", null).use {
                assertTrue(it.moveToFirst())
                assertFalse(it.getString(0).contains("70726976617465"))
                assertFalse(it.getString(1).contains("70726976617465"))
            }
        } finally {
            raw.close()
        }
    }

    @Test
    fun versionOneDatabaseMigratesToVersionThreeWithoutLosingRows() {
        store.close()
        val legacy = object : SQLiteOpenHelper(context, databaseName, null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE conversations (id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL, title_cipher BLOB NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE messages (id TEXT PRIMARY KEY NOT NULL, conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE, role TEXT NOT NULL CHECK(role IN ('USER','ASSISTANT')), content_cipher BLOB NOT NULL, created_at INTEGER NOT NULL)")
                db.execSQL("INSERT INTO conversations VALUES ('legacy-id','legacy-owner',X'0102030405060708090A0B0C0D0E0F101112131415161718',1,1)")
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        legacy.writableDatabase
        legacy.close()

        store = AndroidConversationStore(context, databaseName)
        val migrated = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        try {
            migrated.rawQuery("PRAGMA user_version", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(AndroidConversationStore.DATABASE_VERSION.toLong(), it.getLong(0))
            }
            migrated.rawQuery("SELECT archived FROM conversations WHERE id='legacy-id'", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        } finally {
            migrated.close()
        }
    }

    private fun String.encodeToHex(): String =
        toByteArray(Charsets.UTF_8).joinToString("") { "%02X".format(it) }
}
