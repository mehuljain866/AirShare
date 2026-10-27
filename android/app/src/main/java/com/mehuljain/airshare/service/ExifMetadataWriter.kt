package com.mehuljain.airshare.service

import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.mehuljain.airshare.printer.JobMetadata
import com.mehuljain.airshare.raster.RasterPageInfo
import com.mehuljain.airshare.transfer.DeviceInfoParser
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
            val device = DeviceInfoParser.parse(metadata.userAgent, metadata.userName, metadata.name, null)
            val isApple = metadata.userAgent?.contains("iPad", ignoreCase = true) == true ||
                metadata.userAgent?.contains("iPhone", ignoreCase = true) == true ||
                metadata.userAgent?.contains("Mac", ignoreCase = true) == true ||
                device.deviceDisplayName.contains("iPad", ignoreCase = true) ||
                device.deviceDisplayName.contains("iPhone", ignoreCase = true) ||
                device.deviceDisplayName.contains("Mac", ignoreCase = true)

            val make = when {
                device.deviceDisplayName.contains("Windows", ignoreCase = true) -> "Microsoft"
                device.deviceDisplayName.contains("Android", ignoreCase = true) || device.deviceDisplayName.contains("Samsung", ignoreCase = true) -> "Samsung"
                device.deviceDisplayName.contains("Pixel", ignoreCase = true) -> "Google"
                else -> "Apple"
            }

            val model = when {
                device.deviceDisplayName.contains("iPad", ignoreCase = true) -> "iPad"
                device.deviceDisplayName.contains("iPhone", ignoreCase = true) -> "iPhone"
                device.deviceDisplayName.contains("Mac", ignoreCase = true) -> "Mac"
                device.deviceDisplayName.contains("Windows", ignoreCase = true) -> "Windows PC"
                else -> if (isApple) "iPad" else "AirPrint Receiver"
            }

            val sw = when {
                isApple -> "iOS"
                device.deviceDisplayName.contains("Windows", ignoreCase = true) -> "Windows"
                else -> "AirShare"
            }

            if (exif.getAttribute(ExifInterface.TAG_MAKE) == null) {
                exif.setAttribute(ExifInterface.TAG_MAKE, make)
            }
            if (exif.getAttribute(ExifInterface.TAG_MODEL) == null) {
                exif.setAttribute(ExifInterface.TAG_MODEL, model)
            }
            if (exif.getAttribute(ExifInterface.TAG_SOFTWARE) == null) {
                exif.setAttribute(ExifInterface.TAG_SOFTWARE, sw)
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
