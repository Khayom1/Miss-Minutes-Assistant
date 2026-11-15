package com.khayom1.missminutes

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

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

            preferences.edit().putString(name, "not-valid-ciphertext").commit()
            assertNull(store.read(name))
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
    fun overlayServiceIsNotExportedAndRequiredPermissionsExist() {
        val service = context.packageManager.getServiceInfo(
            ComponentName(context, com.khayom1.missminutes.overlay.OverlayService::class.java),
            0
        )
        assertFalse(service.exported)

        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()
        assert(permissions.contains(android.Manifest.permission.INTERNET))
        assert(permissions.contains(android.Manifest.permission.RECORD_AUDIO))
        assert(permissions.contains(android.Manifest.permission.FOREGROUND_SERVICE))
        assert(permissions.contains(android.Manifest.permission.SYSTEM_ALERT_WINDOW))
    }
}
