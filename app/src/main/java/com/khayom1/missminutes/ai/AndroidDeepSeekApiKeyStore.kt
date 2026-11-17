package com.khayom1.missminutes.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** User-owned DeepSeek credential. Only AES-GCM ciphertext is stored in app preferences. */
interface DeepSeekApiKeyStore {
    fun load(): String?
    fun save(apiKey: String)
    fun clear()
}

class AndroidDeepSeekApiKeyStore(context: Context) : DeepSeekApiKeyStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Synchronized
    override fun load(): String? {
        val encoded = preferences.getString(KEY, null) ?: return null
        return try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            if (payload.size <= IV_BYTES + TAG_BYTES) return clearAndNull()
            val iv = payload.copyOfRange(0, IV_BYTES)
            val ciphertext = payload.copyOfRange(IV_BYTES, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(AAD)
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8).takeIf { it.isNotBlank() }
        } catch (_: GeneralSecurityException) {
            clearAndNull()
        } catch (_: IllegalArgumentException) {
            clearAndNull()
        }
    }

    @Synchronized
    override fun save(apiKey: String) {
        val value = apiKey.trim()
        require(value.startsWith("sk-") && value.length in 20..512) { "Enter a valid DeepSeek API key." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(AAD)
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        check(preferences.edit().putString(KEY, encoded).commit()) { "Unable to securely save the API key." }
    }

    @Synchronized
    override fun clear() {
        val removed = preferences.edit().remove(KEY).commit()
        val invalidated = runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
            true
        }.getOrDefault(false)
        check(removed || invalidated) { "Unable to clear the stored API key." }
    }

    private fun clearAndNull(): String? {
        clear()
        return null
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES = "miss_minutes_deepseek_credentials"
        const val KEY = "deepseek_api_key_v1"
        const val ALIAS = "miss_minutes_deepseek_aes_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / 8
        val AAD = "com.khayom1.missminutes.deepseek.v1".toByteArray(Charsets.UTF_8)
    }
}
