package com.khayom1.missminutes.ai

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

private const val LOCAL_MODEL_EXTENSION = ".litertlm"

internal fun localModelTemporaryFile(directory: File, id: UUID): File =
    File(directory, ".$id.part")

internal fun localModelDestinationFile(directory: File, id: UUID): File =
    File(directory, "$id$LOCAL_MODEL_EXTENSION")
/** Stores imported model weights in app-private storage, separate from secrets and backups. */
class LocalModelStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val modelDirectory = File(appContext.noBackupFilesDir, MODEL_DIRECTORY)

    fun currentModelPath(): String? = preferences.getString(KEY_MODEL_PATH, null)
        ?.takeIf { path -> File(path).let { it.isFile && it.length() > 0L } }

    fun isLocalModelSelected(): Boolean = preferences.getBoolean(KEY_USE_LOCAL_MODEL, false)

    @Synchronized
    fun selectLocalModel(selected: Boolean) {
        require(!selected || currentModelPath() != null) {
            "Import a supported .litertlm model before selecting local inference."
        }
        check(preferences.edit().putBoolean(KEY_USE_LOCAL_MODEL, selected).commit()) {
            "Local model selection could not be saved."
        }
    }

    /** Copies the selected SAF document into private storage; no URI grant is retained. */
    @Synchronized
    @Throws(IOException::class)
    fun importModel(contentResolver: ContentResolver, uri: Uri): String {
        if (!modelDirectory.exists() && !modelDirectory.mkdirs()) {
            throw IOException("Model storage is unavailable.")
        }
        val displayName = queryDisplayName(contentResolver, uri).orEmpty()
        if (displayName.isBlank() || !displayName.lowercase(java.util.Locale.ROOT).endsWith(MODEL_EXTENSION)) {
            throw IOException("Select a .litertlm model file.")
        }

        val temporary = localModelTemporaryFile(modelDirectory, UUID.randomUUID())
        val destination = localModelDestinationFile(modelDirectory, UUID.randomUUID())
        var totalBytes = 0L
        try {
            val input = contentResolver.openInputStream(uri)
                ?: throw IOException("The selected model file could not be opened.")
            input.use { source ->
                FileOutputStream(temporary).use { target ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        if (totalBytes > MAX_MODEL_BYTES) {
                            throw IOException("Model file exceeds the 4 GB import limit.")
                        }
                        target.write(buffer, 0, count)
                    }
                    target.fd.sync()
                }
            }
            if (totalBytes == 0L) throw IOException("The selected model file is empty.")
            if (!temporary.renameTo(destination)) {
                throw IOException("The model file could not be moved into private storage.")
            }
            val oldPath = preferences.getString(KEY_MODEL_PATH, null)
            val committed = preferences.edit()
                .putString(KEY_MODEL_PATH, destination.absolutePath)
                .putBoolean(KEY_USE_LOCAL_MODEL, true)
                .commit()
            if (!committed) {
                destination.delete()
                throw IOException("The imported model path could not be saved.")
            }
            oldPath?.takeIf { it != destination.absolutePath }?.let { File(it).delete() }
            return destination.absolutePath
        } catch (error: Exception) {
            temporary.delete()
            destination.delete()
            if (error is IOException) throw error
            throw IOException("The selected model could not be imported.", error)
        }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else null
            }

    companion object {
        private const val PREFERENCES_NAME = "local_model_preferences"
        private const val MODEL_DIRECTORY = "local-models"
        private const val KEY_MODEL_PATH = "active_model_path"
        private const val KEY_USE_LOCAL_MODEL = "use_local_model"
        private const val MODEL_EXTENSION = LOCAL_MODEL_EXTENSION
        private const val COPY_BUFFER_BYTES = 1024 * 1024
        private const val MAX_MODEL_BYTES = 4L * 1024L * 1024L * 1024L
    }
}
