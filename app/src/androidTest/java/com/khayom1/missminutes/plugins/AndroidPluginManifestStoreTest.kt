package com.khayom1.missminutes.plugins

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPluginManifestStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun declarativeManifestsPersistWithoutConsentState() {
        val store = AndroidPluginManifestStore(context)
        store.replace(emptyList())
        val manifest = AndroidDeviceStatusPlugin.manifest()
        store.replace(listOf(manifest))
        assertEquals(listOf(manifest), AndroidPluginManifestStore(context).load())
        AndroidPluginManifestStore(context).replace(emptyList())
    }

    @Test
    fun restoringManifestDoesNotGrantPluginConsent() {
        val manifest = AndroidDeviceStatusPlugin.manifest()
        val consent = AndroidPluginConsentStore(context)
        consent.setGranted(manifest.id, false)
        AndroidPluginManifestStore(context).replace(listOf(manifest))
        val restored = AndroidPluginManifestStore(context).load().single()
        assertEquals(manifest, restored)
        assertFalse(consent.isGranted(restored.id))
        AndroidPluginManifestStore(context).replace(emptyList())
    }

    @Test
    fun importedManifestMergeReplacesByIdAndStaysMetadataOnly() {
        val store = AndroidPluginManifestStore(context)
        store.replace(emptyList())
        val manifest = AndroidDeviceStatusPlugin.manifest()
        val merged = store.mergeImported(listOf(manifest))
        assertEquals(1, merged.size)
        assertEquals(manifest, merged.single())
        store.replace(emptyList())
    }
}
