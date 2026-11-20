package com.khayom1.missminutes.voice

import android.Manifest

/**
 * Runtime prerequisites for the user-started microphone foreground service.
 *
 * POST_NOTIFICATIONS is intentionally not included: Android allows foreground
 * services to start without notification-drawer permission, while the service
 * must still provide its foreground notification to startForeground().
 */
internal object WakeWordPermissionPolicy {
    val requiredRuntimePermissions: List<String> =
        listOf(Manifest.permission.RECORD_AUDIO)

    fun hasAllRequired(grantedPermissions: Set<String>): Boolean =
        requiredRuntimePermissions.all(grantedPermissions::contains)
}

/** Only an explicit in-app start action may activate continuous microphone capture. */
internal object WakeWordServicePolicy {
    fun shouldStart(action: String?): Boolean =
        action == MissMinutesWakeWordService.ACTION_START
}
