package com.khayom1.missminutes.accessibility

import android.content.Context

data class PendingDangerousAction(
    val controlLabel: String,
    val reasonLabel: String,
    val packageName: String
)

/** One short-lived, single-use approval for the exact dangerous control previously refused. */
object PendingDangerousActionStore {
    private const val PREFERENCES = "miss_minutes_dangerous_action"
    private const val CONTROL_LABEL = "pending_control_label"
    private const val REASON_LABEL = "pending_reason_label"
    private const val PACKAGE_NAME = "pending_package_name"
    private const val CREATED_AT = "created_at"
    private const val MAX_AGE_MILLIS = 2 * 60 * 1_000L

    @Synchronized
    fun save(context: Context, controlLabel: String, reasonLabel: String, packageName: String): Boolean {
        val safeControl = controlLabel.trim().take(128)
        val safeReason = reasonLabel.trim().take(128)
        val safePackage = packageName.trim().take(256)
        if (safeControl.isBlank() || safeReason.isBlank() || safePackage.isBlank()) return false
        return context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(CONTROL_LABEL, safeControl)
            .putString(REASON_LABEL, safeReason)
            .putString(PACKAGE_NAME, safePackage)
            .putLong(CREATED_AT, System.currentTimeMillis())
            .commit()
    }

    @Synchronized
    fun peek(context: Context): PendingDangerousAction? {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val control = preferences.getString(CONTROL_LABEL, null)
        val reason = preferences.getString(REASON_LABEL, null)
        val packageName = preferences.getString(PACKAGE_NAME, null)
        val createdAt = preferences.getLong(CREATED_AT, 0L)
        if (control.isNullOrBlank() || reason.isNullOrBlank() || packageName.isNullOrBlank() ||
            createdAt <= 0L || System.currentTimeMillis() - createdAt !in 0..MAX_AGE_MILLIS
        ) {
            clear(context)
            return null
        }
        return PendingDangerousAction(control, reason, packageName)
    }

    @Synchronized
    fun isConfirmationValid(
        context: Context,
        requestedControl: String,
        currentPackage: String?,
        voiceCommand: String
    ): Boolean {
        val pending = peek(context) ?: return false
        if (AccessibilityAutomationPolicy.normalize(pending.controlLabel) !=
            AccessibilityAutomationPolicy.normalize(requestedControl)
        ) return false
        if (pending.packageName != currentPackage) return false
        return AccessibilityAutomationPolicy.isExplicitDangerousActionConfirmation(
            voiceCommand,
            pending.reasonLabel
        )
    }

    @Synchronized
    fun consumeIfCurrentContext(
        context: Context,
        requestedControl: String,
        currentPackage: String,
        currentReason: String
    ): Boolean {
        val pending = peek(context) ?: return false
        if (AccessibilityAutomationPolicy.normalize(pending.controlLabel) !=
            AccessibilityAutomationPolicy.normalize(requestedControl)
        ) return false
        if (pending.packageName != currentPackage ||
            AccessibilityAutomationPolicy.normalize(pending.reasonLabel) !=
            AccessibilityAutomationPolicy.normalize(currentReason)
        ) return false
        clear(context)
        return true
    }

    @Synchronized
    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .remove(CONTROL_LABEL)
            .remove(REASON_LABEL)
            .remove(PACKAGE_NAME)
            .remove(CREATED_AT)
            .commit()
    }
}
