package com.khayom1.missminutes.plugins

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Persists declarative plugin manifests only. Consent remains in the separate
 * AndroidPluginConsentStore and is never restored from an archive.
 */
class AndroidPluginManifestStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun load(): List<PluginManifest> {
        val encoded = preferences.getString(KEY_MANIFESTS, null) ?: return emptyList()
        return runCatching {
            PluginManifestArchive.decode(Json.parseToJsonElement(encoded).jsonObject)
        }.getOrElse { emptyList() }
    }

    @Synchronized
    fun replace(manifests: Collection<PluginManifest>) {
        val encoded = PluginManifestArchive.encode(manifests.toList()).toString()
        check(preferences.edit().putString(KEY_MANIFESTS, encoded).commit()) {
            "Plugin manifests could not be persisted."
        }
    }

    @Synchronized
    fun remove(pluginId: String): Boolean {
        if (!pluginId.matches(Regex("^[a-z][a-z0-9_.-]{2,63}$"))) return false
        val current = load()
        val updated = current.filterNot { it.id == pluginId }
        if (updated.size == current.size) return false
        replace(updated)
        return true
    }

    @Synchronized
    fun mergeImported(manifests: Collection<PluginManifest>): List<PluginManifest> {
        val merged = load().associateBy { it.id }.toMutableMap()
        manifests.forEach { manifest -> merged[manifest.id] = manifest }
        val result = merged.values.sortedBy { it.id }
        replace(result)
        return result
    }

    companion object {
        private const val PREFERENCES_NAME = "plugin_manifest_store"
        private const val KEY_MANIFESTS = "declarative_manifests_v1"
    }
}
