package com.khayom1.missminutes.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import com.khayom1.missminutes.ai.AiRuntimeProfile
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidAppSettingsStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun appearancePersistsAcrossStoreInstances() {
        val first = AndroidAppSettingsStore(context)
        first.saveAppearance("tg", true)
        val restored = AndroidAppSettingsStore(context).loadAppearance()
        assertEquals(AndroidAppSettingsStore.Appearance("tg", true), restored)
        first.saveAppearance("en", false)
    }

    @Test
    fun runtimeProfilePersistsAcrossStoreInstances() {
        val first = AndroidAppSettingsStore(context)
        first.saveAiRuntimeProfile(AiRuntimeProfile.EXTRA)
        assertEquals(AiRuntimeProfile.EXTRA, AndroidAppSettingsStore(context).loadAiRuntimeProfile())
        first.saveAiRuntimeProfile(AiRuntimeProfile.AUTO)
    }

    @Test
    fun backupAppliesOnlyAllowlistedAppearancePreferences() {
        val store = AndroidAppSettingsStore(context)
        store.applyBackup(SettingsBackupAdapter.Settings("ru", true, 90, false))
        assertEquals(AndroidAppSettingsStore.Appearance("ru", true), store.loadAppearance())
        assertEquals(AiRuntimeProfile.MAX, store.loadAiRuntimeProfile())
        store.saveAppearance("en", false)
        store.saveAiRuntimeProfile(AiRuntimeProfile.AUTO)
    }
}
