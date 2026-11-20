package com.khayom1.missminutes.voice

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordPermissionPolicyTest {
    @Test
    fun microphoneIsTheOnlyRuntimePrerequisiteForTheForegroundListener() {
        assertEquals(
            listOf(Manifest.permission.RECORD_AUDIO),
            WakeWordPermissionPolicy.requiredRuntimePermissions
        )
        assertTrue(
            WakeWordPermissionPolicy.hasAllRequired(
                setOf(Manifest.permission.RECORD_AUDIO)
            )
        )
    }

    @Test
    fun missingMicrophonePermissionPreventsListening() {
        assertFalse(WakeWordPermissionPolicy.hasAllRequired(emptySet()))
        assertFalse(
            WakeWordPermissionPolicy.hasAllRequired(
                setOf(Manifest.permission.POST_NOTIFICATIONS)
            )
        )
    }

    @Test
    fun notificationDrawerPermissionDoesNotGateForegroundServiceStartup() {
        assertTrue(
            WakeWordPermissionPolicy.hasAllRequired(
                setOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            )
        )
    }

    @Test
    fun wakeServiceStartsOnlyForTheExplicitStartAction() {
        assertTrue(WakeWordServicePolicy.shouldStart(MissMinutesWakeWordService.ACTION_START))
        assertFalse(WakeWordServicePolicy.shouldStart(null))
        assertFalse(WakeWordServicePolicy.shouldStart(""))
        assertFalse(WakeWordServicePolicy.shouldStart("com.example.UNEXPECTED"))
        assertFalse(WakeWordServicePolicy.shouldStart(MissMinutesWakeWordService.ACTION_STOP))
    }
}
