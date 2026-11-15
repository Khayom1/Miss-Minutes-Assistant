package com.khayom1.missminutes

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import javax.crypto.Cipher

@RunWith(AndroidJUnit4::class)
class CoreRuntimeInstrumentedTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun apiKeysAreEncryptedAndTamperingFailsClosed() {
        val preferences = context.getSharedPreferences("secure_secrets", Context.MODE_PRIVATE)
        val store = SecretStore(context)
        val name = "instrumentation_secret"
        val secret = "miss-minutes-test-secret-123"
        try {
            store.save(name, secret)
            val ciphertext = preferences.getString(name, null)
            assertNotNull(ciphertext)
            assertFalse(ciphertext!!.contains(secret))
            assertEquals(secret, store.read(name))

            preferences.edit().putString("different_secret_name", ciphertext).commit()
            assertNull(store.read("different_secret_name"))
            preferences.edit().remove("different_secret_name").commit()

            preferences.edit().putString(name, "not-valid-ciphertext").commit()
            assertNull(store.read(name))
            preferences.edit().putInt(name, 7).commit()
            assertNull(store.read(name))
        } finally {
            store.save(name, "")
            store.save("different_secret_name", "")
        }
    }

    @Test
    fun legacyEncryptedKeysAreReadableAndMigrated() {
        val preferences = context.getSharedPreferences("secure_secrets", Context.MODE_PRIVATE)
        val store = SecretStore(context)
        val name = "legacy_instrumentation_secret"
        val secret = "legacy-miss-minutes-key"
        try {
            store.save(name, "seed")
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = keyStore.getKey("miss_minutes_aes_key", null) as java.security.Key
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val legacyPayload = cipher.iv + cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
            preferences.edit().putString(name, Base64.encodeToString(legacyPayload, Base64.NO_WRAP)).commit()

            assertEquals(secret, store.read(name))
            val migrated = Base64.decode(preferences.getString(name, null), Base64.NO_WRAP)
            assertArrayEquals(byteArrayOf(0x4d, 0x4d, 0x4b, 0x31), migrated.copyOfRange(0, 4))
            assertTrue(!String(migrated, Charsets.UTF_8).contains(secret))
        } finally {
            store.save(name, "")
        }
    }

    @Test
    fun databaseRetentionLimitsAreEnforced() {
        val database = MemoryDatabase(context)
        try {
            database.writableDatabase.delete("personal_memory", null, null)
            database.writableDatabase.delete("notifications", null, null)

            repeat(510) { database.savePersonalMemory("instrumentation", "entry-$it") }
            val personalCount = database.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM personal_memory", null
            ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            assertEquals(500, personalCount)

            repeat(60) { database.saveNotification("test.package", "title-$it", "body-$it") }
            val notificationCount = database.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM notifications", null
            ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            assertEquals(50, notificationCount)
        } finally {
            database.close()
            context.deleteDatabase("miss_minutes_memory.db")
        }
    }

    @Test
    fun databaseUpgradesVersionOneWithoutDroppingStoredMemory() {
        val database = MemoryDatabase(context)
        database.writableDatabase.delete("personal_memory", null, null)
        database.savePersonalMemory("upgrade-test", "preserve-this-row")
        database.writableDatabase.version = 1
        database.close()

        val upgraded = MemoryDatabase(context)
        try {
            val db = upgraded.readableDatabase
            assertEquals(2, db.version)
            val stored = db.rawQuery(
                "SELECT body FROM personal_memory WHERE kind = ?",
                arrayOf("upgrade-test")
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            assertEquals("preserve-this-row", stored)
            val indexExists = db.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?",
                arrayOf("idx_notifications_package_time")
            ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) == 1 }
            assertTrue(indexExists)
        } finally {
            upgraded.close()
            context.deleteDatabase("miss_minutes_memory.db")
        }
    }

    @Test
    fun overlayServiceIsNotExportedAndRequiredPermissionsExist() {
        val overlayService = context.packageManager.getServiceInfo(
            ComponentName(context, com.khayom1.missminutes.overlay.OverlayService::class.java),
            0
        )
        assertFalse(overlayService.exported)
        val notificationService = context.packageManager.getServiceInfo(
            ComponentName(context, NotificationReaderService::class.java), 0
        )
        assertEquals("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", notificationService.permission)
        val accessibilityService = context.packageManager.getServiceInfo(
            ComponentName(context, DeviceAccessibilityService::class.java), 0
        )
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE", accessibilityService.permission)

        val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertFalse(applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        assertFalse(applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0)

        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()
        assertTrue(permissions.contains(android.Manifest.permission.INTERNET))
        assertTrue(permissions.contains(android.Manifest.permission.RECORD_AUDIO))
        assertTrue(permissions.contains(android.Manifest.permission.FOREGROUND_SERVICE))
        assertTrue(permissions.contains(android.Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE))
        assertTrue(permissions.contains(android.Manifest.permission.SYSTEM_ALERT_WINDOW))
        assertTrue(permissions.contains(android.Manifest.permission.POST_NOTIFICATIONS))
        assertTrue(permissions.contains("com.android.alarm.permission.SET_ALARM"))
    }
}
