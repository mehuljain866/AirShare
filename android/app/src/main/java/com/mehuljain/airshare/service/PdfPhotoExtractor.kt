package com.mehuljain.airshare.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.mehuljain.airshare.printer.ConvertedDocument
import com.mehuljain.airshare.printer.JobMetadata
import com.mehuljain.airshare.raster.PixelFormat
import com.mehuljain.airshare.raster.RasterPageInfo
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

/**
 * Extracts raw embedded JPEG photos from single-image PDF files without re-encoding,
 * or renders single-page screenshot/raster PDFs (e.g. from iPad/iPhone) into high-resolution photos,
 * automatically removing any A4 paper outlines or margins and preserving original EXIF metadata.
 */
object PdfPhotoExtractor {
    private const val TAG = "PdfPhotoExtractor"
    private val JPEG_SOI = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val JPEG_EOI = byteArrayOf(0xFF.toByte(), 0xD9.toByte())

    /**
     * Attempts to extract or render a single full-page photo or screenshot from [source].
     * Returns a ConvertedDocument if a clean photo was extracted/rendered, or null otherwise.
     */
    fun extractSingleImage(
        source: File,
        metadata: JobMetadata,
        target: (extension: String) -> File,
    ): ConvertedDocument? {
        if (!source.exists() || source.length() < 100) return null
        return try {
            val bytes = source.readBytes()
            val text = String(bytes, Charsets.ISO_8859_1)

            // If the PDF has multiple /Page objects, keep it as a multi-page PDF
            val pageCount = countOccurrences(text, "/Type /Page\n") +
                countOccurrences(text, "/Type /Page\r") +
                countOccurrences(text, "/Type /Page ") +
                countOccurrences(text, "/Type/Page")
            if (pageCount > 1) return null

            // Check if this single-page PDF represents an image, photo, or screenshot
            val hasImageObject = text.contains("/Subtype /Image") || text.contains("/Subtype/Image") || text.contains("/Image")
            val hasDct = text.contains("/DCTDecode")
            val isImageJob = isImageJobName(metadata.name)

            if (!hasImageObject && !hasDct && !isImageJob) {
                // Text-only PDF document, keep as PDF
                return null
            }

            // Extract embedded creation date and software producer from PDF
            val pdfDate = extractPdfDate(text)
            val producer = extractProducer(text)
            val creator = extractCreator(text)
            val effectiveMetadata = metadata.copy(
                createdAt = if (pdfDate > 0) pdfDate else metadata.createdAt,
                producer = producer ?: metadata.producer,
                creator = creator ?: metadata.creator,
            )

            // Strategy 1: If raw JPEG (/DCTDecode) stream exists, extract original JPEG bytes without recompression
            if (hasDct) {
                val soiPos = indexOf(bytes, JPEG_SOI)
                val eoiPos = if (soiPos >= 0) lastIndexOf(bytes, JPEG_EOI, soiPos) else -1
                if (soiPos >= 0 && eoiPos > soiPos) {
                    val jpegLength = (eoiPos + 2) - soiPos
                    if (jpegLength >= 1024) {
                        val out = target("jpg")
                        FileOutputStream(out).use { fos ->
                            fos.write(bytes, soiPos, jpegLength)
                        }
                        val dimensions = findImageDimensions(text)
                        val pageInfo = dimensions?.let { (w, h) ->
                            RasterPageInfo(w, h, 300, 300, PixelFormat.RGB_24)
                        }
                        ExifMetadataWriter.writeMetadata(out, effectiveMetadata, pageInfo)
                        return ConvertedDocument(out, "image/jpeg")
                    }
                }
            }

            // Strategy 2: Render single-page PDF using Android's native PdfRenderer
            // with exact image viewport mapping and white-margin autocropping (eliminates A4 outlines).
            renderPdfToImage(source, effectiveMetadata, text, target)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to extract image from PDF: ${source.name}", t)
            null
        }
    }

    private fun renderPdfToImage(
        source: File,
        metadata: JobMetadata,
        text: String,
        target: (extension: String) -> File,
    ): ConvertedDocument? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount != 1) return null

            val page = renderer.openPage(0)
            try {
                val embeddedDims = findImageDimensions(text)
                val transform = findImagePlacement(text, page.height.toFloat())

                val renderedBmp: Bitmap = if (transform != null && embeddedDims != null) {
                    // Method A: Render exact image viewport directly, bypassing the surrounding A4 canvas
                    val safeW = embeddedDims.first.coerceIn(100, 4096)
                    val safeH = embeddedDims.second.coerceIn(100, 4096)
                    val bmp = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)

                    val matrix = Matrix().apply {
                        postTranslate(-transform.posX, -transform.topPoint)
                        postScale(safeW.toFloat() / transform.renderW, safeH.toFloat() / transform.renderH)
                    }
                    val canvas = Canvas(bmp)
                    canvas.drawColor(Color.WHITE)
                    page.render(bmp, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    bmp
                } else {
                    // Method B: Render full page at 300 DPI, then autocrop white paper borders
                    val scale = 300f / 72f
                    val pageW = (page.width * scale).toInt().coerceIn(1024, 4096)
                    val pageH = (page.height * scale).toInt().coerceIn(1024, 4096)
                    val bmp = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    canvas.drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    bmp
                }

                // Autocrop any remaining solid white print borders to guarantee no A4 outlines
                val finalBmp = autocropWhiteBorders(renderedBmp)

                val out = target("jpg")
                FileOutputStream(out).use { fos ->
                    finalBmp.compress(Bitmap.CompressFormat.JPEG, 96, fos)
                }
                if (finalBmp != renderedBmp) {
                    finalBmp.recycle()
                }
                renderedBmp.recycle()

                val pageInfo = RasterPageInfo(finalBmp.width, finalBmp.height, 300, 300, PixelFormat.RGB_24)
                ExifMetadataWriter.writeMetadata(out, metadata, pageInfo)
                ConvertedDocument(out, "image/jpeg")
            } finally {
                page.close()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "PdfRenderer rendering failed: ${source.name}", t)
            null
        } finally {
            try { renderer?.close() } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
        }
    }

    /**
     * Removes uniform white paper margins from print canvases (e.g. A4 Letter margins).
     */
    fun autocropWhiteBorders(bmp: Bitmap): Bitmap {
        val w = bmp.width
        val h = bmp.height
        if (w <= 100 || h <= 100) return bmp

        val rowPixels = IntArray(w)

        // Find top margin
        var top = 0
        while (top < h) {
            bmp.getPixels(rowPixels, 0, w, 0, top, w, 1)
            if (!isRowWhite(rowPixels)) break
            top++
        }
        if (top >= h) return bmp // Entire image is white

        // Find bottom margin
        var bottom = h - 1
        while (bottom > top) {
            bmp.getPixels(rowPixels, 0, w, 0, bottom, w, 1)
            if (!isRowWhite(rowPixels)) break
            bottom--
        }

        // Find left margin
        val colH = bottom - top + 1
        val colPixels = IntArray(colH)
        var left = 0
        while (left < w) {
            bmp.getPixels(colPixels, 0, 1, left, top, 1, colH)
            if (!isColWhite(colPixels)) break
            left++
        }

        // Find right margin
        var right = w - 1
        while (right > left) {
            bmp.getPixels(colPixels, 0, 1, right, top, 1, colH)
            if (!isColWhite(colPixels)) break
            right--
        }

        val cropW = right - left + 1
        val cropH = bottom - top + 1

        // If margins were minimal (<= 4px total), preserve original
        if (left <= 4 && (w - 1 - right) <= 4 && top <= 4 && (h - 1 - bottom) <= 4) {
            return bmp
        }
        if (cropW < 100 || cropH < 100) return bmp

        return try {
            val cropped = Bitmap.createBitmap(bmp, left, top, cropW, cropH)
            cropped
        } catch (_: Throwable) {
            bmp
        }
    }

    private fun isRowWhite(pixels: IntArray): Boolean {
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (r < 248 || g < 248 || b < 248) return false
        }
        return true
    }

    private fun isColWhite(pixels: IntArray): Boolean {
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (r < 248 || g < 248 || b < 248) return false
        }
        return true
    }

    private class ImagePlacement(
        val renderW: Float,
        val renderH: Float,
        val posX: Float,
        val topPoint: Float,
    )

    private fun findImagePlacement(text: String, pageHeight: Float): ImagePlacement? {
        try {
            // Find PDF transformation matrix: a 0 0 d e f cm
            val pattern = Pattern.compile("([0-9.]+)\\s+0(?:\\.0+)?\\s+0(?:\\.0+)?\\s+([0-9.]+)\\s+([0-9.]+)\\s+([0-9.]+)\\s+cm")
            val matcher = pattern.matcher(text)
            var best: ImagePlacement? = null
            var maxArea = 0f
            while (matcher.find()) {
                val a = matcher.group(1)?.toFloatOrNull() ?: 0f
                val d = matcher.group(2)?.toFloatOrNull() ?: 0f
                val e = matcher.group(3)?.toFloatOrNull() ?: 0f
                val f = matcher.group(4)?.toFloatOrNull() ?: 0f
                val area = a * d
                if (area > maxArea && a > 50f && d > 50f) {
                    maxArea = area
                    val top = pageHeight - (f + d)
                    best = ImagePlacement(a, d, e, top)
                }
            }
            return best
        } catch (_: Exception) {
            return null
        }
    }

    private fun isImageJobName(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".heic") || lower.endsWith(".webp") ||
            lower.contains("screenshot") || lower.contains("screen shot") ||
            lower.contains("photo") || lower.contains("image") || lower.contains("picture") ||
            lower.startsWith("img_") || lower.startsWith("dsc_")
    }

    private fun extractPdfDate(text: String): Long {
        try {
            val cdMatcher = Pattern.compile("/CreationDate\\s*\\((?:D:)?(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(?:([+-Z])(?:(\\d{2})'?(\\d{2})'?)?)?").matcher(text)
            if (cdMatcher.find()) {
                val y = cdMatcher.group(1)?.toInt() ?: return 0L
                val m = (cdMatcher.group(2)?.toInt() ?: 1) - 1
                val d = cdMatcher.group(3)?.toInt() ?: 1
                val hh = cdMatcher.group(4)?.toInt() ?: 0
                val mm = cdMatcher.group(5)?.toInt() ?: 0
                val ss = cdMatcher.group(6)?.toInt() ?: 0
                val tzSign = cdMatcher.group(7)
                val tzH = cdMatcher.group(8)?.toInt() ?: 0
                val tzM = cdMatcher.group(9)?.toInt() ?: 0
                val tz = when {
                    tzSign == "Z" -> TimeZone.getTimeZone("UTC")
                    tzSign == "+" || tzSign == "-" -> TimeZone.getTimeZone(String.format(Locale.US, "GMT%s%02d:%02d", tzSign, tzH, tzM))
                    else -> TimeZone.getDefault()
                }
                val cal = Calendar.getInstance(tz).apply {
                    set(y, m, d, hh, mm, ss)
                    set(Calendar.MILLISECOND, 0)
                }
                return cal.timeInMillis
            }

            val mdMatcher = Pattern.compile("/ModDate\\s*\\((?:D:)?(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})").matcher(text)
            if (mdMatcher.find()) {
                val y = mdMatcher.group(1)?.toInt() ?: return 0L
                val m = (mdMatcher.group(2)?.toInt() ?: 1) - 1
                val d = mdMatcher.group(3)?.toInt() ?: 1
                val hh = mdMatcher.group(4)?.toInt() ?: 0
                val mm = mdMatcher.group(5)?.toInt() ?: 0
                val ss = mdMatcher.group(6)?.toInt() ?: 0
                val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
                    set(y, m, d, hh, mm, ss)
                    set(Calendar.MILLISECOND, 0)
                }
                return cal.timeInMillis
            }
        } catch (_: Exception) {}
        return 0L
    }

    private fun extractProducer(text: String): String? {
        val m = Pattern.compile("/Producer\\s*\\(([^)]+)\\)").matcher(text)
        return if (m.find()) m.group(1)?.trim() else null
    }

    private fun extractCreator(text: String): String? {
        val m = Pattern.compile("/Creator\\s*\\(([^)]+)\\)").matcher(text)
        return if (m.find()) m.group(1)?.trim() else null
    }

    private fun findImageDimensions(text: String): Pair<Int, Int>? {
        try {
            // Find /Width and /Height inside image object dictionaries
            val imgBlockPattern = Pattern.compile("/Subtype\\s*/Image[\\s\\S]*?>>")
            val blockMatcher = imgBlockPattern.matcher(text)
            var maxW = 0
            var maxH = 0
            while (blockMatcher.find()) {
                val block = blockMatcher.group(0)
                val wM = Pattern.compile("/Width\\s+(\\d+)").matcher(block)
                val hM = Pattern.compile("/Height\\s+(\\d+)").matcher(block)
                if (wM.find() && hM.find()) {
                    val w = wM.group(1)?.toIntOrNull() ?: 0
                    val h = hM.group(1)?.toIntOrNull() ?: 0
                    if (w * h > maxW * maxH) {
                        maxW = w
                        maxH = h
                    }
                }
            }
            if (maxW > 100 && maxH > 100) return Pair(maxW, maxH)

            // Fallback global search
            val wM = Pattern.compile("/Width\\s+(\\d+)").matcher(text)
            val hM = Pattern.compile("/Height\\s+(\\d+)").matcher(text)
            while (wM.find() && hM.find()) {
                val w = wM.group(1)?.toIntOrNull() ?: 0
                val h = hM.group(1)?.toIntOrNull() ?: 0
                if (w * h > maxW * maxH) {
                    maxW = w
                    maxH = h
                }
            }
            if (maxW > 100 && maxH > 100) return Pair(maxW, maxH)
        } catch (_: Exception) {}
        return null
    }

    private fun countOccurrences(str: String, sub: String): Int {
        var count = 0
        var idx = 0
        while (str.indexOf(sub, idx).also { idx = it } >= 0) {
            count++
            idx += sub.length
        }
        return count
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray, start: Int = 0): Int {
        outer@ for (i in start..data.size - pattern.size) {
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun lastIndexOf(data: ByteArray, pattern: ByteArray, minStart: Int): Int {
        outer@ for (i in (data.size - pattern.size) downTo minStart) {
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
