package com.khayom1.missminutes.plugins

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPluginConsentStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun pluginConsentIsDefaultDenyAndStoredPerPlugin() {
        val store = AndroidPluginConsentStore(context)
        store.revokeAll()
        assertFalse(store.isGranted("android_device_status"))
        assertFalse(store.isGranted("unknown_plugin"))

        store.setGranted("android_device_status", true)
        assertTrue(store.isGranted("android_device_status"))
        assertFalse(store.isGranted("another_plugin"))
        store.setGranted("android_device_status", false)
        assertFalse(store.isGranted("android_device_status"))
    }

    @Test
    fun pluginAuditLogIsBoundedAndContainsNoArguments() {
        val logFile = java.io.File(context.filesDir, "plugin-audit.log")
        logFile.delete()
        val log = FilePluginAuditLog(context)
        repeat(5_000) { index ->
            log.append(
                PluginAuditEntry(
                    timestampMillis = index.toLong(),
                    pluginId = "android_device_status",
                    actionId = "device_info",
                    outcome = "success"
                )
            )
        }
        assertTrue(logFile.length() <= 64 * 1024L)
        assertFalse(logFile.readText().contains("private-user-argument"))
        logFile.delete()
    }

    @Test
    fun invalidPluginIdCannotCreateConsentEntry() {
        val store = AndroidPluginConsentStore(context)
        try {
            store.setGranted("../untrusted", true)
            throw AssertionError("Expected invalid plugin id rejection.")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
    @Test
    fun consentChangesAreDurableAndCanBeRevokedIndividuallyOrGlobally() {
        val preferences = context.getSharedPreferences("plugin_user_consent", Context.MODE_PRIVATE)
        assertTrue(preferences.edit().clear().commit())
        val store = AndroidPluginConsentStore(context)

        store.setGranted("test_plugin", true)
        assertTrue(AndroidPluginConsentStore(context).isGranted("test_plugin"))

        store.setGranted("test_plugin", false)
        assertFalse(AndroidPluginConsentStore(context).isGranted("test_plugin"))

        store.setGranted("test_plugin", true)
        store.setGranted("another_plugin", true)
        store.revokeAll()
        assertFalse(AndroidPluginConsentStore(context).isGranted("test_plugin"))
        assertFalse(AndroidPluginConsentStore(context).isGranted("another_plugin"))
    }

}
