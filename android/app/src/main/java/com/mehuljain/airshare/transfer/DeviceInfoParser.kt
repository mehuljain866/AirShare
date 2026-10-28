package com.mehuljain.airshare.transfer

import java.util.Locale
import java.util.regex.Pattern

object DeviceInfoParser {
    data class ParsedInfo(
        val deviceDisplayName: String,
        val contentDisplayName: String,
        val isPhoto: Boolean,
        val isVideo: Boolean = false,
        val destinationStorage: String, // "Gallery" or "Documents"
    )

    private val GENERIC_USERS = setOf(
        "mobile", "root", "anonymous", "owner", "guest", "admin", "user", "default", "system",
    )

    private val GENERIC_JOB_NAMES = setOf(
        "untitled", "print job", "print-job", "document", "photo or document", "video",
    )

    fun parse(
        userAgent: String?,
        userName: String?,
        jobName: String?,
        format: String?,
    ): ParsedInfo {
        val detectedDevice = detectDevice(userAgent)
        val cleanUser = cleanUserName(userName)

        val deviceDisplayName = when {
            cleanUser != null && detectedDevice != null -> {
                if (cleanUser.contains(detectedDevice, ignoreCase = true)) cleanUser
                else "$cleanUser's $detectedDevice"
            }
            cleanUser != null -> cleanUser
            detectedDevice != null -> detectedDevice
            else -> "Nearby device"
        }

        val isVid = isVideoFormat(format, jobName)
        val isImg = if (isVid) false else isImageFormat(format, jobName)
        val contentDisplayName = parseContentDescription(jobName, format, isImg, isVid)
        val destinationStorage = if (isImg || isVid) "Gallery" else "Documents"

        return ParsedInfo(
            deviceDisplayName = deviceDisplayName,
            contentDisplayName = contentDisplayName,
            isPhoto = isImg,
            isVideo = isVid,
            destinationStorage = destinationStorage,
        )
    }

    private fun detectDevice(ua: String?): String? {
        if (ua.isNullOrBlank()) return null
        val lower = ua.lowercase(Locale.ROOT)
        return when {
            lower.contains("iphone") -> "iPhone"
            lower.contains("ipad") -> "iPad"
            lower.contains("macintosh") || lower.contains("mac os x") || lower.contains("darwin") -> "Mac"
            lower.contains("windows") -> "Windows PC"
            lower.contains("android") || lower.contains("samsung") || lower.contains("pixel") -> "Android phone"
            lower.contains("linux") -> "Linux PC"
            else -> null
        }
    }

    private fun cleanUserName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val trimmed = name.trim()
        if (trimmed.lowercase(Locale.ROOT) in GENERIC_USERS) return null
        return trimmed
    }

    private fun isVideoFormat(format: String?, jobName: String?): Boolean {
        val fmt = format?.lowercase(Locale.ROOT) ?: ""
        if (fmt.startsWith("video/")) return true
        val job = jobName?.lowercase(Locale.ROOT) ?: ""
        val videoExts = listOf(".mp4", ".mov", ".m4v", ".mkv", ".webm", ".avi", ".3gp")
        if (videoExts.any { job.endsWith(it) }) return true
        val videoKeywords = listOf("video", "movie", "clip", "recording")
        return videoKeywords.any { job.contains(it) }
    }

    private fun isImageFormat(format: String?, jobName: String?): Boolean {
        val fmt = format?.lowercase(Locale.ROOT) ?: ""
        if (fmt.startsWith("image/") || fmt == "image/urf" || fmt == "image/pwg-raster") return true
        val job = jobName?.lowercase(Locale.ROOT) ?: ""
        if (job.endsWith(".jpg") || job.endsWith(".jpeg") || job.endsWith(".png") ||
            job.endsWith(".heic") || job.endsWith(".webp") || job.startsWith("img_") || job.startsWith("dsc_")) {
            return true
        }
        val keywords = listOf(
            "photo", "image", "picture", "screenshot", "screen shot", "screen-shot",
            "snapshot", "capture", "camera", "dcim", "wallpaper",
        )
        return keywords.any { job.contains(it) }
    }

    private fun parseContentDescription(rawJob: String?, format: String?, isImage: Boolean, isVideo: Boolean = false): String {
        val job = rawJob?.trim().orEmpty()
        val lower = job.lowercase(Locale.ROOT)

        if (isVideo) {
            val countMatch = Pattern.compile("(\\d+)\\s*(?:videos?|movies?|clips?|recordings?)", Pattern.CASE_INSENSITIVE).matcher(job)
            if (countMatch.find()) {
                val count = countMatch.group(1)?.toIntOrNull() ?: 1
                return if (count == 1) "a video" else "$count videos"
            }
            if (job.isNotBlank() && lower !in GENERIC_JOB_NAMES && !lower.startsWith("share_") && !lower.startsWith("upload_")) {
                return job
            }
            return "a video"
        }

        // Check for multiple count, e.g. "3 photos", "Photos (5)"
        val countMatch = Pattern.compile("(\\d+)\\s*(?:photos?|images?|pictures?|files?|items?)", Pattern.CASE_INSENSITIVE).matcher(job)
        if (countMatch.find()) {
            val count = countMatch.group(1)?.toIntOrNull() ?: 1
            return if (isImage) (if (count == 1) "a photo" else "$count photos")
                   else (if (count == 1) "a document" else "$count documents")
        }

        val parenMatch = Pattern.compile("photos?\\s*\\((\\d+)\\)", Pattern.CASE_INSENSITIVE).matcher(job)
        if (parenMatch.find()) {
            val count = parenMatch.group(1)?.toIntOrNull() ?: 1
            return if (count == 1) "a photo" else "$count photos"
        }

        if (lower.contains("screenshot") || lower.contains("screen shot")) {
            return "a screenshot"
        }

        if (lower == "photo" || lower == "photos" || lower == "image" || lower == "images" || lower == "picture") {
            return "a photo"
        }

        // Single item
        if (job.isNotBlank() && lower !in GENERIC_JOB_NAMES) {
            // If it's a filename like IMG_1234.JPG or Report.pdf, return as is
            return job
        }

        return if (isImage) "a photo" else "a document"
    }
}
