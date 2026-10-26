package com.mehuljain.airshare.service

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.mehuljain.airshare.printer.DocumentExporter
import java.io.File
import java.io.IOException

/**
 * Copies finished photos into Pictures/AirShare and documents into Documents/AirShare
 * via MediaStore (no storage permission needed on API 29+).
 */
class MediaStoreExporter(context: Context) : DocumentExporter {
    private val resolver = context.applicationContext.contentResolver

    override fun export(source: File, format: String, displayName: String): String {
        val isImage = format.lowercase().startsWith("image/")
        val collection = if (isImage) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        var uri: Uri? = insert(collection, displayName, format, isImage)
            ?: insert(collection, withSuffix(displayName), format, isImage)
        if (uri == null) throw IOException("MediaStore insert failed for $displayName")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Cannot open $uri")
            out.use { o -> source.inputStream().use { it.copyTo(o) } }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return uri.toString()
        } catch (e: Exception) {
            try { resolver.delete(uri!!, null, null) } catch (_: Exception) {}
            throw if (e is IOException) e else IOException("Export failed", e)
        }
    }

    override fun delete(uri: String) {
        try {
            resolver.delete(Uri.parse(uri), null, null)
        } catch (e: Exception) {
            Log.w("AirShare", "Export delete failed for $uri", e)
        }
    }

    private fun insert(collection: Uri, name: String, mime: String, isImage: Boolean): Uri? = try {
        val relPath = if (isImage) {
            Environment.DIRECTORY_PICTURES + "/AirShare"
        } else {
            Environment.DIRECTORY_DOCUMENTS + "/AirShare"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            if (isImage) {
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            }
        }
        resolver.insert(collection, values)
    } catch (e: Exception) {
        Log.w("AirShare", "MediaStore insert failed for $name", e)
        null
    }

    private fun withSuffix(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) + " (2)" + name.substring(dot) else "$name (2)"
    }
}
