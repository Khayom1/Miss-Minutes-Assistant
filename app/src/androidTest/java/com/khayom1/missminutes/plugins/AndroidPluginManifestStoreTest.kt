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

    @Test
    fun importedManifestWithExistingIdCannotReplaceStoredDefinition() {
        val store = AndroidPluginManifestStore(context)
        val original = AndroidDeviceStatusPlugin.manifest()
        store.replace(listOf(original))

        val merged = store.mergeImported(listOf(original.copy(displayName = "Untrusted replacement")))

        assertEquals(listOf(original), merged)
        assertEquals(listOf(original), store.load())
        store.replace(emptyList())
    }

    @Test
    fun removeDeletesOnlyTheSelectedPersistedManifest() {
        val store = AndroidPluginManifestStore(context)
        store.replace(listOf(AndroidDeviceStatusPlugin.manifest()))
        assertEquals(true, store.remove(AndroidDeviceStatusPlugin.PLUGIN_ID))
        assertEquals(emptyList<PluginManifest>(), store.load())
        assertEquals(false, store.remove(AndroidDeviceStatusPlugin.PLUGIN_ID))
    }

    @Test
    fun corruptStoredManifestArchiveFailsClosedToEmptyRegistryInput() {
        context.getSharedPreferences("plugin_manifest_store", Context.MODE_PRIVATE)
            .edit().putString("declarative_manifests_v1", """{"schemaVersion":999,"plugins":[]}""").commit()
        assertEquals(emptyList<PluginManifest>(), AndroidPluginManifestStore(context).load())
        context.getSharedPreferences("plugin_manifest_store", Context.MODE_PRIVATE)
            .edit().remove("declarative_manifests_v1").commit()
    }
}
