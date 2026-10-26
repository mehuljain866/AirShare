package com.mehuljain.airshare.service

import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.mehuljain.airshare.printer.JobMetadata
import com.mehuljain.airshare.raster.RasterPageInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Writes and preserves EXIF metadata on saved JPEG photos using [ExifInterface].
 */
object ExifMetadataWriter {
    private const val TAG = "ExifMetadataWriter"

    fun writeMetadata(
        file: File,
        metadata: JobMetadata,
        pageInfo: RasterPageInfo? = null,
    ) {
        if (!file.exists() || file.length() == 0L) return
        try {
            val exif = ExifInterface(file.absolutePath)

            // Date & Time
            val date = Date(if (metadata.createdAt > 0) metadata.createdAt else System.currentTimeMillis())
            val dateFmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }
            val dateStr = dateFmt.format(date)

            if (exif.getAttribute(ExifInterface.TAG_DATETIME) == null) {
                exif.setAttribute(ExifInterface.TAG_DATETIME, dateStr)
            }
            if (exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) == null) {
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateStr)
            }
            if (exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED) == null) {
                exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateStr)
            }

            // Image description & Document name from print job name
            val cleanName = metadata.name.trim()
            if (cleanName.isNotBlank() && cleanName != "Untitled" && cleanName != "photo") {
                if (exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION) == null) {
                    exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, cleanName)
                }
            }

            // Artist from sender requesting-user-name
            val user = metadata.userName.trim()
            if (user.isNotBlank() && user != "anonymous") {
                if (exif.getAttribute(ExifInterface.TAG_ARTIST) == null) {
                    exif.setAttribute(ExifInterface.TAG_ARTIST, user)
                }
            }

            // Software & Model
            if (exif.getAttribute(ExifInterface.TAG_SOFTWARE) == null) {
                exif.setAttribute(ExifInterface.TAG_SOFTWARE, "AirShare")
            }
            if (exif.getAttribute(ExifInterface.TAG_MAKE) == null) {
                exif.setAttribute(ExifInterface.TAG_MAKE, "AirShare")
            }
            if (exif.getAttribute(ExifInterface.TAG_MODEL) == null) {
                exif.setAttribute(ExifInterface.TAG_MODEL, "AirPrint Receiver")
            }

            // Resolution & dimensions if available from raster
            if (pageInfo != null) {
                if (pageInfo.dpiX > 0) {
                    exif.setAttribute(ExifInterface.TAG_X_RESOLUTION, "${pageInfo.dpiX}/1")
                }
                if (pageInfo.dpiY > 0) {
                    exif.setAttribute(ExifInterface.TAG_Y_RESOLUTION, "${pageInfo.dpiY}/1")
                }
                exif.setAttribute(ExifInterface.TAG_RESOLUTION_UNIT, "2") // inches
                if (pageInfo.widthPx > 0) {
                    exif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, pageInfo.widthPx.toString())
                }
                if (pageInfo.heightPx > 0) {
                    exif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, pageInfo.heightPx.toString())
                }
            }

            exif.saveAttributes()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to write EXIF attributes to ${file.name}", t)
        }
    }
}
