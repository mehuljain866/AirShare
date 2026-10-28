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
 * Copies finished photos into Pictures/AirShare, videos into Movies/AirShare,
 * and documents into Documents/AirShare via MediaStore (no storage permission needed on API 29+).
 */
class MediaStoreExporter(context: Context) : DocumentExporter {
    private val resolver = context.applicationContext.contentResolver

    override fun export(source: File, format: String, displayName: String): String {
        val f = format.lowercase()
        val isImage = f.startsWith("image/")
        val isVideo = f.startsWith("video/") || displayName.endsWith(".mp4", ignoreCase = true) ||
            displayName.endsWith(".mov", ignoreCase = true) || displayName.endsWith(".m4v", ignoreCase = true) ||
            displayName.endsWith(".mkv", ignoreCase = true)

        val collection = when {
            isImage -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            isVideo -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val dateTaken = extractDateTaken(source, isImage)
        var uri: Uri? = insert(collection, displayName, format, isImage, isVideo, dateTaken)
            ?: insert(collection, withSuffix(displayName), format, isImage, isVideo, dateTaken)
        if (uri == null) throw IOException("MediaStore insert failed for $displayName")
        try {
            if (isImage) {
                try {
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                    val bmp = android.graphics.BitmapFactory.decodeFile(source.absolutePath, opts)
                    if (bmp != null) {
                        com.mehuljain.airshare.transfer.TransferCoordinator.setThumbnail(0, bmp)
                    }
                } catch (_: Exception) {}
            } else if (isVideo) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(source.absolutePath)
                    val bmp = retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    retriever.release()
                    if (bmp != null) {
                        com.mehuljain.airshare.transfer.TransferCoordinator.setThumbnail(0, bmp)
                    }
                } catch (_: Exception) {}
            }
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

    private fun insert(collection: Uri, name: String, mime: String, isImage: Boolean, isVideo: Boolean, dateTaken: Long): Uri? = try {
        val relPath = when {
            isImage -> Environment.DIRECTORY_PICTURES + "/AirShare"
            isVideo -> Environment.DIRECTORY_MOVIES + "/AirShare"
            else -> Environment.DIRECTORY_DOCUMENTS + "/AirShare"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            if (isImage || isVideo) {
                put(MediaStore.MediaColumns.DATE_TAKEN, dateTaken)
                put(MediaStore.MediaColumns.DATE_ADDED, dateTaken / 1000)
                put(MediaStore.MediaColumns.DATE_MODIFIED, dateTaken / 1000)
            }
        }
        resolver.insert(collection, values)
    } catch (e: Exception) {
        Log.w("AirShare", "MediaStore insert failed for $name", e)
        null
    }

    private fun extractDateTaken(source: File, isImage: Boolean): Long {
        if (!isImage) return System.currentTimeMillis()
        return try {
            val exif = androidx.exifinterface.media.ExifInterface(source.absolutePath)
            val dtStr = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME)
            if (!dtStr.isNullOrBlank()) {
                val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getDefault()
                }
                val parsed = sdf.parse(dtStr)
                if (parsed != null && parsed.time > 0) parsed.time else System.currentTimeMillis()
            } else {
                System.currentTimeMillis()
            }
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
    }

    private fun withSuffix(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) + " (2)" + name.substring(dot) else "$name (2)"
    }
}
