package com.khayom1.missminutes.memory

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreConversationCipherTest {
    private val keyAlias = "miss_minutes_cipher_test_" + UUID.randomUUID().toString().replace("-", "")
    private lateinit var cipher: AndroidKeystoreConversationCipher

    @Before
    fun createCipher() {
        cipher = AndroidKeystoreConversationCipher(keyAlias)
    }

    @After
    fun removeTestKey() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (keyStore.containsAlias(keyAlias)) keyStore.deleteEntry(keyAlias)
    }

    @Test
    fun ciphertextDoesNotContainPlaintextAndRoundTrips() {
        val plaintext = "private conversation content"
        val encrypted = cipher.encrypt("owner-a", plaintext)

        assertNotEquals(plaintext, encrypted)
        assertTrue(!encrypted.contains(plaintext))
        assertEquals(plaintext, cipher.decrypt("owner-a", encrypted))
    }

    @Test
    fun ciphertextCannotBeMovedBetweenOwnerScopes() {
        val encrypted = cipher.encrypt("owner-a", "owner-scoped secret")

        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            cipher.decrypt("owner-b", encrypted)
        }
    }

    @Test
    fun ciphertextUsesFreshRandomIvForEveryEncryption() {
        val first = cipher.encrypt("owner-a", "same plaintext")
        val second = cipher.encrypt("owner-a", "same plaintext")

        assertNotEquals(first, second)
        assertEquals("same plaintext", cipher.decrypt("owner-a", first))
        assertEquals("same plaintext", cipher.decrypt("owner-a", second))
    }

    @Test
    fun tamperedCiphertextFailsAuthentication() {
        val encrypted = cipher.encrypt("owner-a", "tamper-evident content")
        val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        val tampered = Base64.encodeToString(bytes, Base64.NO_WRAP)

        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            cipher.decrypt("owner-a", tampered)
        }
    }

    @Test
    fun malformedCiphertextIsRejected() {
        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            cipher.decrypt("owner-a", "not-valid-base64!")
        }
    }
}
