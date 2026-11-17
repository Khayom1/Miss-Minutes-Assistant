package com.khayom1.missminutes.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidAppSettingsStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun appearancePersistsAcrossStoreInstances() {
        val first = AndroidAppSettingsStore(context)
        first.saveAppearance("Tajik", true)
        val restored = AndroidAppSettingsStore(context).loadAppearance()
        assertEquals(AndroidAppSettingsStore.Appearance("Tajik", true), restored)
        first.saveAppearance("English", false)
    }

    @Test
    fun backupAppliesOnlyAllowlistedAppearancePreferences() {
        val store = AndroidAppSettingsStore(context)
        store.applyBackup(SettingsBackupAdapter.Settings("Russian", true, 90, false))
        assertEquals(AndroidAppSettingsStore.Appearance("Russian", true), store.loadAppearance())
        store.saveAppearance("English", false)
    }
}
