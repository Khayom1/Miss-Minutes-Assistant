package com.khayom1.missminutes.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSessionStoreTest {
    private lateinit var context: Context
    private lateinit var store: AndroidSessionStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("miss_minutes_secure_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = AndroidSessionStore(context)
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun sessionRoundTripsAsCiphertextOnlyAndCanBeCleared() {
        val session = SupabaseAuthSession(
            accessToken = "access-token-never-store-this-in-plaintext",
            refreshToken = "refresh-token-never-store-this-in-plaintext",
            expiresAtEpochSeconds = 1_800_000_000L,
            userId = "user-123",
            email = "user@example.test"
        )

        store.save(session)

        assertEquals(session, store.load())
        val persistedValue = context.getSharedPreferences(
            "miss_minutes_secure_session",
            Context.MODE_PRIVATE
        ).getString("encrypted_session_v1", null)

        assertNotNull(persistedValue)
        assertFalse(persistedValue.orEmpty().contains(session.accessToken))
        assertFalse(persistedValue.orEmpty().contains(session.refreshToken))

        store.clear()
        assertNull(store.load())
    }

    @Test
    fun corruptedCiphertextFailsClosedAndDeletesStoredSession() {
        val preferences = context.getSharedPreferences(
            "miss_minutes_secure_session",
            Context.MODE_PRIVATE
        )
        preferences.edit().putString("encrypted_session_v1", "%%%invalid%%%").commit()

        assertNull(store.load())
        assertNull(preferences.getString("encrypted_session_v1", null))
    }
}
