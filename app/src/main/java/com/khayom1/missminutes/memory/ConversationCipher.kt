package com.khayom1.missminutes.memory

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts conversation content before it reaches SQLite. The owner id is
 * authenticated as AES-GCM additional data, preventing ciphertext from being
 * copied between local account scopes.
 */
interface ConversationCipher {
    fun encrypt(ownerId: String, plaintext: String): String
    fun decrypt(ownerId: String, ciphertext: String): String
}

class AndroidKeystoreConversationCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) : ConversationCipher {
    @Synchronized
    override fun encrypt(ownerId: String, plaintext: String): String {
        require(ownerId.isNotBlank())
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(aad(ownerId))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(byteArrayOf(FORMAT_VERSION) + cipher.iv + encrypted, Base64.NO_WRAP)
    }

    @Synchronized
    override fun decrypt(ownerId: String, ciphertext: String): String {
        require(ownerId.isNotBlank())
        val payload = try {
            Base64.decode(ciphertext, Base64.NO_WRAP)
        } catch (error: IllegalArgumentException) {
            throw IllegalStateException("Stored conversation data is unreadable.", error)
        }
        if (payload.size <= 1 + IV_LENGTH + GCM_TAG_BYTES || payload[0] != FORMAT_VERSION) {
            throw IllegalStateException("Stored conversation data has an unsupported format.")
        }
        try {
            val iv = payload.copyOfRange(1, 1 + IV_LENGTH)
            val encrypted = payload.copyOfRange(1 + IV_LENGTH, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad(ownerId))
            return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        } catch (error: GeneralSecurityException) {
            throw IllegalStateException("Stored conversation data could not be authenticated.", error)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val specification = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(specification)
        return generator.generateKey()
    }

    private fun aad(ownerId: String): ByteArray =
        "com.khayom1.missminutes.conversation.v1|$ownerId".toByteArray(Charsets.UTF_8)

    private companion object {
        const val DEFAULT_KEY_ALIAS = "miss_minutes_conversation_aes_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION: Byte = 1
        const val IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
    }
}
