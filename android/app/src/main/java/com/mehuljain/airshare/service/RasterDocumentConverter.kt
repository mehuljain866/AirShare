package com.mehuljain.airshare.service

import android.util.Log
import com.mehuljain.airshare.printer.ConvertedDocument
import com.mehuljain.airshare.printer.DocumentConverter
import com.mehuljain.airshare.printer.JobMetadata
import com.mehuljain.airshare.printer.PrinterConfig
import com.mehuljain.airshare.raster.JpegEncoder
import com.mehuljain.airshare.raster.RasterDecoder
import com.mehuljain.airshare.raster.RasterPageInfo
import com.mehuljain.airshare.raster.RasterSink
import com.mehuljain.airshare.raster.RasterToPdf
import java.io.File
import java.io.FileOutputStream

/**
 * Converts incoming raster / PDF documents into photos (JPEG) or PDFs according to user settings.
 */
class RasterDocumentConverter(
    private val jpegEncoder: JpegEncoder?,
    private val config: () -> PrinterConfig = { PrinterConfig(name = "default", uuid = "default") },
) : DocumentConverter {

    override fun convert(
        source: File,
        format: String,
        metadata: JobMetadata,
        target: (extension: String) -> File,
    ): ConvertedDocument? {
        val f = format.lowercase()
        val currentConfig = config()

        // 1. If format is PDF, check if we should extract embedded JPEG photos
        if (f == "application/pdf" || (f == "application/octet-stream" && sniffsPdf(source))) {
            if (currentConfig.extractPdfPhotos) {
                val extracted = PdfPhotoExtractor.extractSingleImage(source, metadata, target)
                if (extracted != null) return extracted
            }
            return null // Keep as PDF
        }

        // 2. If format is JPEG, ensure EXIF is populated and return null (keep original JPEG)
        if (f == "image/jpeg") {
            ExifMetadataWriter.writeMetadata(source, metadata)
            return null
        }

        // 3. If format is raster (Apple URF / PWG Raster)
        val isRaster = f == "image/urf" || f == "image/pwg-raster" ||
            (f == "application/octet-stream" && sniffsRaster(source))
        if (!isRaster) return null

        // If user wants photos saved as images, check if single page
        if (currentConfig.savePhotosAsImages && jpegEncoder != null) {
            val imageDoc = tryConvertRasterToImage(source, metadata, target)
            if (imageDoc != null) return imageDoc
        }

        // Multi-page or PDF mode: convert to PDF
        val out = target("pdf")
        try {
            source.inputStream().buffered().use { input ->
                out.outputStream().buffered().use { os -> RasterToPdf.convert(input, os, jpegEncoder) }
            }
        } catch (e: Throwable) {
            out.delete()
            throw e
        }
        return ConvertedDocument(out, "application/pdf")
    }

    private fun tryConvertRasterToImage(
        source: File,
        metadata: JobMetadata,
        target: (extension: String) -> File,
    ): ConvertedDocument? {
        val pages = mutableListOf<DecodedPage>()
        try {
            source.inputStream().buffered().use { input ->
                RasterDecoder.decode(input, object : RasterSink {
                    private var currentInfo: RasterPageInfo? = null
                    private var currentBuf: ByteArray? = null
                    private var pos = 0

                    override fun beginPage(info: RasterPageInfo) {
                        currentInfo = info
                        currentBuf = ByteArray(Math.multiplyExact(info.rowBytes, info.heightPx))
                        pos = 0
                    }

                    override fun row(row: ByteArray) {
                        val b = currentBuf ?: return
                        val n = currentInfo!!.rowBytes
                        if (pos + n > b.size) return
                        System.arraycopy(row, 0, b, pos, n)
                        pos += n
                    }

                    override fun endPage() {
                        val info = currentInfo ?: return
                        val b = currentBuf ?: return
                        currentInfo = null
                        currentBuf = null
                        // Only buffer the first page; if more pages arrive, record marker for multi-page
                        if (pages.isEmpty()) {
                            pages.add(DecodedPage(info, b))
                        } else {
                            pages.add(DecodedPage(info, ByteArray(0)))
                        }
                    }
                })
            }
        } catch (e: Throwable) {
            Log.w("AirShare", "Raster decode for image conversion failed, falling back to PDF", e)
            return null
        }

        // If exactly 1 page was decoded, save as JPEG photo
        if (pages.size == 1 && jpegEncoder != null) {
            val page = pages[0]
            val jpegBytes = jpegEncoder.encode(
                page.info.widthPx,
                page.info.heightPx,
                page.info.pixelFormat,
                page.pixels,
                quality = 95,
            )
            if (jpegBytes != null) {
                val out = target("jpg")
                try {
                    FileOutputStream(out).use { it.write(jpegBytes) }
                    ExifMetadataWriter.writeMetadata(out, metadata, page.info)
                    return ConvertedDocument(out, "image/jpeg")
                } catch (t: Throwable) {
                    out.delete()
                    Log.w("AirShare", "Writing JPEG photo failed", t)
                }
            }
        }
        return null
    }

    private class DecodedPage(val info: RasterPageInfo, val pixels: ByteArray)

    private fun sniffsRaster(source: File): Boolean = try {
        val head = ByteArray(8)
        val n = source.inputStream().use { it.read(head) }
        n > 0 && RasterDecoder.sniff(head.copyOf(n)) != null
    } catch (e: Exception) {
        false
    }

    private fun sniffsPdf(source: File): Boolean = try {
        val head = ByteArray(4)
        val n = source.inputStream().use { it.read(head) }
        n >= 4 && head[0] == '%'.code.toByte() && head[1] == 'P'.code.toByte() &&
            head[2] == 'D'.code.toByte() && head[3] == 'F'.code.toByte()
    } catch (e: Exception) {
        false
    }
}
