package com.khayom1.missminutes.plugins

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Bounded metadata-only audit log. No plugin inputs or output are persisted. */
class FilePluginAuditLog(context: Context) : PluginAuditLog {
    private val file = File(context.applicationContext.filesDir, "plugin-audit.log")

    @Synchronized
    override fun append(entry: PluginAuditEntry) {
        val safePlugin = entry.pluginId.takeIf { it.matches(Regex("^[a-z][a-z0-9_.-]{2,63}$")) }
            ?: throw IOException("Invalid plugin audit identifier")
        val safeAction = entry.actionId.takeIf { it.matches(Regex("^[a-z][a-z0-9_.-]{1,47}$")) }
            ?: throw IOException("Invalid plugin audit action")
        val safeOutcome = entry.outcome.take(32).replace(Regex("[^a-z_]"), "")
        val bytes = "${entry.timestampMillis}\t$safePlugin\t$safeAction\t$safeOutcome\n"
            .toByteArray(Charsets.UTF_8)
        if (file.exists() && file.length() + bytes.size > MAX_BYTES && !file.delete()) {
            throw IOException("Unable to rotate plugin audit log")
        }
        FileOutputStream(file, true).use { it.write(bytes) }
    }

    companion object {
        private const val MAX_BYTES = 64 * 1024L
    }
}
