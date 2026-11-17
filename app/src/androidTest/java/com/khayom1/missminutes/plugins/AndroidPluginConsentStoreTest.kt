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
    fun consentChangesAreDurableAndCanBeRevokedIndividuallyOrGlobally() {
        val preferences = context.getSharedPreferences("plugin_user_consent", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
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
