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
    fun invalidPluginIdCannotCreateConsentEntry() {
        val store = AndroidPluginConsentStore(context)
        try {
            store.setGranted("../untrusted", true)
            throw AssertionError("Expected invalid plugin id rejection.")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
