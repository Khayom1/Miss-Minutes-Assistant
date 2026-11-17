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

    /** Returns newest metadata-only events; malformed lines are ignored safely. */
    @Synchronized
    fun readRecent(limit: Int = 20): List<PluginAuditEntry> {
        require(limit in 1..100)
        if (!file.isFile) return emptyList()
        return runCatching { file.readLines(Charsets.UTF_8) }
            .getOrDefault(emptyList())
            .asReversed()
            .asSequence()
            .mapNotNull { line ->
                val fields = line.split('\t')
                if (fields.size != 4) return@mapNotNull null
                val timestamp = fields[0].toLongOrNull() ?: return@mapNotNull null
                val pluginId = fields[1].takeIf { it.matches(Regex("^[a-z][a-z0-9_.-]{2,63}$")) }
                    ?: return@mapNotNull null
                val actionId = fields[2].takeIf { it.matches(Regex("^[a-z][a-z0-9_.-]{1,47}$")) }
                    ?: return@mapNotNull null
                val outcome = fields[3].takeIf { it.matches(Regex("^[a-z_]{1,32}$")) }
                    ?: return@mapNotNull null
                PluginAuditEntry(timestamp, pluginId, actionId, outcome)
            }
            .take(limit)
            .toList()
    }

    companion object {
        private const val MAX_BYTES = 64 * 1024L
    }
}
