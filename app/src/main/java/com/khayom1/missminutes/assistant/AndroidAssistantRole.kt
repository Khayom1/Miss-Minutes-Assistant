package com.khayom1.missminutes.assistant

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build

/** User-consented Android Assistant role access. Role ownership is not permission ownership. */
object AndroidAssistantRole {
    fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val manager = context.getSystemService(RoleManager::class.java) ?: return false
        return manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)
    }

    fun isHeld(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val manager = context.getSystemService(RoleManager::class.java) ?: return false
        return manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
    }

    fun createRequestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val manager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) ||
            manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
        ) return null
        return manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
    }
}
