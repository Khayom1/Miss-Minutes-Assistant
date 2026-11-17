package com.khayom1.missminutes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khayom1.missminutes.ai.AndroidDeepSeekApiKeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeepSeekApiKeyStoreTest {
    @Test
    fun encryptsPersistsAndRemovesUserKey() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AndroidDeepSeekApiKeyStore(context)
        val key = "sk-user-owned-instrumentation-test-key-123456"
        store.clear()

        store.save(key)
        assertEquals(key, store.load())

        val preferences = context.getSharedPreferences("miss_minutes_deepseek_credentials", android.content.Context.MODE_PRIVATE)
        val ciphertext = preferences.getString("deepseek_api_key_v1", null).orEmpty()
        assertFalse(ciphertext.isEmpty())
        assertFalse(ciphertext.contains(key))

        store.clear()
        assertNull(store.load())
    }
}
