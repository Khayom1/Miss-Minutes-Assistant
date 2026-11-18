package com.khayom1.missminutes.ai

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val LOCAL_MODEL_EXTENSION = ".litertlm"

internal fun localModelTemporaryFile(directory: File, id: UUID): File =
    File(directory, ".$id.part")

internal fun localModelDestinationFile(directory: File, id: UUID): File =
    File(directory, "$id$LOCAL_MODEL_EXTENSION")

/** Resolve only a direct child of the private model directory; canonical paths reject traversal and symlinks. */
internal fun canonicalLocalModelFile(directory: File, path: String): File? = runCatching {
    val root = directory.canonicalFile
    val candidate = File(path).canonicalFile
    candidate.takeIf {
        it.parentFile == root &&
            it.name.endsWith(LOCAL_MODEL_EXTENSION, ignoreCase = true)
    }
}.getOrNull()

/** A model is ready only while its validated file still matches the imported size and timestamp. */
internal fun validatedLocalModelFile(
    directory: File,
    path: String,
    expectedSize: Long,
    expectedLastModified: Long
): File? {
    if (expectedSize <= 0L || expectedLastModified < 0L) return null
    val file = canonicalLocalModelFile(directory, path) ?: return null
    return file.takeIf {
        it.isFile &&
            it.length() == expectedSize &&
            it.lastModified() == expectedLastModified
    }
}

/** Stores validated model weights in app-private storage, separate from secrets and backups. */
class LocalModelStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val modelDirectory = File(appContext.noBackupFilesDir, MODEL_DIRECTORY)
    private val importMutex = Mutex()

    fun currentModelPath(): String? {
        val path = preferences.getString(KEY_MODEL_PATH, null) ?: return null
        return validatedLocalModelFile(
            directory = modelDirectory,
            path = path,
            expectedSize = preferences.getLong(KEY_MODEL_SIZE, -1L),
            expectedLastModified = preferences.getLong(KEY_MODEL_MODIFIED, -1L)
        )?.absolutePath
    }

    fun isLocalModelSelected(): Boolean =
        preferences.getBoolean(KEY_USE_LOCAL_MODEL, false) && currentModelPath() != null

    @Synchronized
    fun selectLocalModel(selected: Boolean) {
        require(!selected || currentModelPath() != null) {
            "Import and validate a compatible .litertlm model before selecting local inference."
        }
        check(preferences.edit().putBoolean(KEY_USE_LOCAL_MODEL, selected).commit()) {
            "Local model selection could not be saved."
        }
    }

    /**
     * Copies a SAF document into private storage, initializes the exact staged file
     * through LiteRT-LM, then atomically activates it. A failed validation or commit
     * leaves the previously selected model and its file untouched.
     */
    suspend fun importModel(
        contentResolver: ContentResolver,
        uri: Uri,
        validateModel: suspend (String) -> Unit
    ): String = importMutex.withLock {
        withContext(Dispatchers.IO) {
        if (!modelDirectory.exists() && !modelDirectory.mkdirs()) {
            throw IOException("Model storage is unavailable.")
        }
        val displayName = queryDisplayName(contentResolver, uri).orEmpty()
        if (displayName.isBlank() || !displayName.lowercase(Locale.ROOT).endsWith(LOCAL_MODEL_EXTENSION)) {
            throw IOException("Select a .litertlm model file.")
        }

        val temporary = localModelTemporaryFile(modelDirectory, UUID.randomUUID())
        val destination = localModelDestinationFile(modelDirectory, UUID.randomUUID())
        var activated = false
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

            val stagedFile = canonicalLocalModelFile(modelDirectory, destination.absolutePath)
                ?: throw IOException("The staged model path is outside private storage.")
            validateModel(stagedFile.absolutePath)

            val oldPath = currentModelPath()
            val validatedSize = stagedFile.length()
            val validatedLastModified = stagedFile.lastModified()
            if (validatedSize <= 0L) throw IOException("The validated model file is empty.")

            val committed = preferences.edit()
                .putString(KEY_MODEL_PATH, stagedFile.absolutePath)
                .putLong(KEY_MODEL_SIZE, validatedSize)
                .putLong(KEY_MODEL_MODIFIED, validatedLastModified)
                .putBoolean(KEY_USE_LOCAL_MODEL, true)
                .commit()
            if (!committed) {
                throw IOException("The validated model could not be activated.")
            }
            activated = true

            oldPath?.takeIf { it != stagedFile.absolutePath }?.let { old ->
                canonicalLocalModelFile(modelDirectory, old)?.delete()
            }
            stagedFile.absolutePath
        } catch (error: CancellationException) {
            temporary.delete()
            if (!activated) destination.delete()
            throw error
        } catch (error: Exception) {
            temporary.delete()
            if (!activated) destination.delete()
            if (error is IOException) throw error
            throw IOException("The selected model is not a compatible LiteRT-LM model.", error)
        }
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
        private const val KEY_MODEL_SIZE = "validated_model_size"
        private const val KEY_MODEL_MODIFIED = "validated_model_modified"
        private const val KEY_USE_LOCAL_MODEL = "use_local_model"
        private const val COPY_BUFFER_BYTES = 1024 * 1024
        private const val MAX_MODEL_BYTES = 4L * 1024L * 1024L * 1024L
    }
}
