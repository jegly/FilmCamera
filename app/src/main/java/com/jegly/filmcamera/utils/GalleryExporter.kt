package com.jegly.filmcamera.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GalleryExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Saves the processed JPEG to the public Pictures/FilmCamera directory
     * via MediaStore (API 29+) or legacy file copy.
     *
     * @return The [Uri] of the saved image.
     */
    suspend fun saveToGallery(file: File): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val filename = "FilmCamera_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FilmCamera")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: error("MediaStore insert returned null")

                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                uri
            } else {
                @Suppress("DEPRECATION")
                val picturesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "FilmCamera",
                ).apply { mkdirs() }

                val dest = File(picturesDir, filename)
                file.copyTo(dest, overwrite = true)

                Uri.fromFile(dest)
            }
        }
    }

    /**
     * Queries MediaStore for the most recently saved FilmCamera photo.
     * Returns null if no photos exist yet. Used to populate the VIEW button on startup.
     */
    fun getLatestPhotoUri(): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%FilmCamera%")
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, sortOrder,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                } else null
            }
        } else {
            @Suppress("DEPRECATION")
            val picturesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "FilmCamera",
            )
            picturesDir.listFiles()
                ?.filter { it.extension.lowercase() == "jpg" }
                ?.maxByOrNull { it.lastModified() }
                ?.let { Uri.fromFile(it) }
        }
    }

    /**
     * Converts a private cache [File] to a content:// [Uri] via FileProvider
     * so it can be shared with other apps.
     */
    fun toShareableUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
