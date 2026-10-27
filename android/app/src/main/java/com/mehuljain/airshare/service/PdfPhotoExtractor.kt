package com.mehuljain.airshare.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.mehuljain.airshare.printer.ConvertedDocument
import com.mehuljain.airshare.printer.JobMetadata
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
 * preserving original EXIF metadata.
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

            // Extract embedded creation date from PDF if available
            val pdfDate = extractPdfDate(text)
            val effectiveMetadata = if (pdfDate > 0) metadata.copy(createdAt = pdfDate) else metadata

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
                            RasterPageInfo(1, w, h, 300, 300)
                        }
                        ExifMetadataWriter.writeMetadata(out, effectiveMetadata, pageInfo)
                        return ConvertedDocument(out, "image/jpeg")
                    }
                }
            }

            // Strategy 2: Render single-page PDF using Android's native PdfRenderer (handles screenshots, FlateDecode, raster)
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
                // If the PDF embeds an image with known dimensions (e.g. 2048x1536 iPad Retina screenshot),
                // render at exact native dimensions. Otherwise render at crisp 300 DPI.
                val embeddedDims = findImageDimensions(text)
                val (w, h) = if (embeddedDims != null) {
                    embeddedDims
                } else {
                    val scale = 300f / 72f
                    Pair((page.width * scale).toInt().coerceAtLeast(1024), (page.height * scale).toInt().coerceAtLeast(1024))
                }

                val safeW = w.coerceIn(100, 4096)
                val safeH = h.coerceIn(100, 4096)

                val bitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                val out = target("jpg")
                FileOutputStream(out).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 96, fos)
                }
                bitmap.recycle()

                val pageInfo = RasterPageInfo(1, safeW, safeH, 300, 300)
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
            val matcher = Pattern.compile("/CreationDate\\s*\\((?:D:)?(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})").matcher(text)
            if (matcher.find()) {
                val y = matcher.group(1)?.toInt() ?: return 0L
                val m = (matcher.group(2)?.toInt() ?: 1) - 1
                val d = matcher.group(3)?.toInt() ?: 1
                val hh = matcher.group(4)?.toInt() ?: 0
                val mm = matcher.group(5)?.toInt() ?: 0
                val ss = matcher.group(6)?.toInt() ?: 0
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    set(y, m, d, hh, mm, ss)
                    set(Calendar.MILLISECOND, 0)
                }
                return cal.timeInMillis
            }
        } catch (_: Exception) {}
        return 0L
    }

    private fun findImageDimensions(text: String): Pair<Int, Int>? {
        try {
            val wMatcher = Pattern.compile("/Width\\s+(\\d+)").matcher(text)
            val hMatcher = Pattern.compile("/Height\\s+(\\d+)").matcher(text)
            var maxW = 0
            var maxH = 0
            while (wMatcher.find() && hMatcher.find()) {
                val w = wMatcher.group(1)?.toIntOrNull() ?: 0
                val h = hMatcher.group(1)?.toIntOrNull() ?: 0
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
