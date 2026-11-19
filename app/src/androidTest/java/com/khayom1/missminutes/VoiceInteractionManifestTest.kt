package com.khayom1.missminutes

import android.content.ComponentName
import android.content.pm.PackageManager
import android.service.voice.VoiceInteractionService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khayom1.missminutes.assistant.MissMinutesRecognitionService
import com.khayom1.missminutes.assistant.MissMinutesVoiceInteractionService
import com.khayom1.missminutes.assistant.MissMinutesVoiceInteractionSessionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceInteractionManifestTest {
    @Test
    fun assistantAndRecognitionServicesMeetAndroidManifestContracts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = context.packageManager

        val interaction = packageManager.getServiceInfo(
            ComponentName(context, MissMinutesVoiceInteractionService::class.java),
            PackageManager.GET_META_DATA
        )
        assertTrue(interaction.exported)
        assertEquals("android.permission.BIND_VOICE_INTERACTION", interaction.permission)
        assertTrue(
            (interaction.metaData?.getInt(VoiceInteractionService.SERVICE_META_DATA) ?: 0) != 0
        )

        val session = packageManager.getServiceInfo(
            ComponentName(context, MissMinutesVoiceInteractionSessionService::class.java),
            PackageManager.GET_META_DATA
        )
        assertTrue(session.exported)
        assertEquals("android.permission.BIND_VOICE_INTERACTION", session.permission)

        val recognition = packageManager.getServiceInfo(
            ComponentName(context, MissMinutesRecognitionService::class.java),
            PackageManager.GET_META_DATA
        )
        assertTrue(recognition.exported)
        assertNull(recognition.permission)
        assertNotNull(recognition.metaData)
        assertTrue(
            packageManager.queryIntentServices(
                android.content.Intent("android.speech.RecognitionService"),
                PackageManager.GET_META_DATA
            ).any { it.serviceInfo?.name == MissMinutesRecognitionService::class.java.name }
        )
    }
}
