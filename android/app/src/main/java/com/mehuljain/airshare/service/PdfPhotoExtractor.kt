package com.mehuljain.airshare.service

import android.util.Log
import com.mehuljain.airshare.printer.ConvertedDocument
import com.mehuljain.airshare.printer.JobMetadata
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Extracts raw embedded JPEG photos from single-image PDF files without re-encoding,
 * preserving original EXIF metadata.
 */
object PdfPhotoExtractor {
    private const val TAG = "PdfPhotoExtractor"
    private val JPEG_SOI = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val JPEG_EOI = byteArrayOf(0xFF.toByte(), 0xD9.toByte())

    /**
     * Attempts to extract a single full-page JPEG photo from [source].
     * Returns a ConvertedDocument if a clean embedded JPEG was found, or null otherwise.
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

            // Look for DCTDecode filter indicator in PDF
            if (!text.contains("/DCTDecode")) return null

            // If the PDF has multiple /Page objects, keep it as a multi-page PDF
            val pageCount = countOccurrences(text, "/Type /Page\n") + countOccurrences(text, "/Type /Page ") +
                countOccurrences(text, "/Type/Page")
            if (pageCount > 1) return null

            // Find JPEG SOI (FF D8 FF)
            val soiPos = indexOf(bytes, JPEG_SOI)
            if (soiPos < 0) return null

            // Find JPEG EOI (FF D9) after SOI
            val eoiPos = lastIndexOf(bytes, JPEG_EOI, soiPos)
            if (eoiPos <= soiPos) return null

            val jpegLength = (eoiPos + 2) - soiPos
            if (jpegLength < 1024) return null // too small to be a photo

            val out = target("jpg")
            FileOutputStream(out).use { fos ->
                fos.write(bytes, soiPos, jpegLength)
            }

            ExifMetadataWriter.writeMetadata(out, metadata)
            ConvertedDocument(out, "image/jpeg")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to extract image from PDF: ${source.name}", t)
            null
        }
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
