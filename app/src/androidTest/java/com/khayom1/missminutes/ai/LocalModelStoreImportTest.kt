package com.khayom1.missminutes.ai

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.test.mock.MockContentResolver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalModelStoreImportTest {
    @Test
    fun failedCompatibilityValidationPreservesPreviouslySelectedModel() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("local_model_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val modelDirectory = File(context.noBackupFilesDir, "local-models")
        modelDirectory.deleteRecursively()

        val firstSource = File(context.cacheDir, "${UUID.randomUUID()}.litertlm")
            .apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val firstResolver = resolverFor(context, firstSource)
        val store = LocalModelStore(context)

        try {
            val firstPath = store.importModel(
                firstResolver,
                Uri.parse("content://$AUTHORITY/first")
            ) { stagedPath ->
                assertTrue(File(stagedPath).isFile)
            }
            assertEquals(firstPath, store.currentModelPath())
            assertTrue(store.isLocalModelSelected())

            val rejectedSource = File(context.cacheDir, "${UUID.randomUUID()}.litertlm")
                .apply { writeBytes(byteArrayOf(9, 8, 7, 6)) }
            try {
                store.importModel(
                    resolverFor(context, rejectedSource),
                    Uri.parse("content://$AUTHORITY/rejected")
                ) {
                    throw IOException("synthetic incompatible model")
                }
                throw AssertionError("Incompatible model import must fail.")
            } catch (_: IOException) {
                // The store must keep the previously validated selection.
            }

            assertEquals(firstPath, store.currentModelPath())
            assertTrue(File(firstPath).isFile)
            assertTrue(store.isLocalModelSelected())
            assertFalse(modelDirectory.listFiles().orEmpty().any { it.name.endsWith(".part") })
            rejectedSource.delete()
        } finally {
            firstSource.delete()
            modelDirectory.deleteRecursively()
            context.getSharedPreferences("local_model_preferences", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
    }

    private fun resolverFor(context: Context, source: File): MockContentResolver {
        val provider = FileBackedProvider(source)
        provider.attachInfo(
            context,
            ProviderInfo().apply {
                authority = AUTHORITY
                packageName = context.packageName
                applicationInfo = context.applicationInfo
            }
        )
        return MockContentResolver(context).apply {
            addProvider(AUTHORITY, provider)
        }
    }

    private class FileBackedProvider(
        private val source: File
    ) : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply {
            addRow(arrayOf("test-model.litertlm"))
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (!source.isFile) throw FileNotFoundException(source.absolutePath)
            return ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun getType(uri: Uri): String = "application/octet-stream"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0
    }

    private companion object {
        const val AUTHORITY = "miss-minutes-local-model-test"
    }
}
