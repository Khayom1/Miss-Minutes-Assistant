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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * AES-256-GCM encrypted session persistence backed by a non-exportable
 * Android Keystore key. SharedPreferences contain ciphertext only.
 */
class AndroidSessionStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : SessionStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    override fun load(): SupabaseAuthSession? {
        val encoded = preferences.getString(SESSION_KEY, null) ?: return null

        return try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            if (payload.size <= IV_LENGTH + GCM_TAG_LENGTH_BYTES) {
                clear()
                return null
            }

            val iv = payload.copyOfRange(0, IV_LENGTH)
            val ciphertext = payload.copyOfRange(IV_LENGTH, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(AAD_BYTES)
            val plaintext = cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
            val stored = json.decodeFromString(StoredSession.serializer(), plaintext)

            if (
                stored.accessToken.isBlank() ||
                stored.refreshToken.isBlank() ||
                stored.userId.isBlank() ||
                stored.expiresAtEpochSeconds <= 0
            ) {
                clear()
                null
            } else {
                SupabaseAuthSession(
                    accessToken = stored.accessToken,
                    refreshToken = stored.refreshToken,
                    expiresAtEpochSeconds = stored.expiresAtEpochSeconds,
                    userId = stored.userId,
                    email = stored.email
                )
            }
        } catch (_: GeneralSecurityException) {
            clear()
            null
        } catch (_: IllegalArgumentException) {
            clear()
            null
        } catch (_: SerializationException) {
            clear()
            null
        }
    }

    @Synchronized
    override fun save(session: SupabaseAuthSession) {
        require(session.accessToken.isNotBlank())
        require(session.refreshToken.isNotBlank())
        require(session.userId.isNotBlank())
        require(session.expiresAtEpochSeconds > 0)

        val stored = StoredSession(
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            expiresAtEpochSeconds = session.expiresAtEpochSeconds,
            userId = session.userId,
            email = session.email
        )
        val plaintext = json.encodeToString(StoredSession.serializer(), stored)
            .toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(AAD_BYTES)
        val ciphertext = cipher.doFinal(plaintext)
        val payload = cipher.iv + ciphertext
        val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)

        check(preferences.edit().putString(SESSION_KEY, encoded).commit()) {
            "Unable to persist encrypted session."
        }
    }

    @Synchronized
    override fun clear() {
        preferences.edit().remove(SESSION_KEY).commit()
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val specification = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
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

    @Serializable
    private data class StoredSession(
        val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("expires_at") val expiresAtEpochSeconds: Long,
        @SerialName("user_id") val userId: String,
        val email: String?
    )

    private companion object {
        const val PREFERENCES_NAME = "miss_minutes_secure_session"
        const val SESSION_KEY = "encrypted_session_v1"
        const val KEY_ALIAS = "miss_minutes_session_aes_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_LENGTH_BYTES = GCM_TAG_BITS / 8
        val AAD_BYTES = "com.khayom1.missminutes.session.v1".toByteArray(Charsets.UTF_8)
    }
}
