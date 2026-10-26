package com.mehuljain.airshare.transfer

import java.io.FilterInputStream
import java.io.InputStream

/**
 * FilterInputStream that monitors bytes read and calls [onProgress] periodically.
 */
class ProgressInputStream(
    input: InputStream,
    private val totalBytes: Long,
    private val onProgress: (bytesRead: Long, totalBytes: Long) -> Unit,
) : FilterInputStream(input) {
    private var bytesRead = 0L
    private var lastReportedTime = 0L

    override fun read(): Int {
        val b = super.read()
        if (b != -1) {
            bytesRead++
            reportIfNeeded()
        }
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val r = super.read(b, off, len)
        if (r > 0) {
            bytesRead += r
            reportIfNeeded()
        }
        return r
    }

    private fun reportIfNeeded(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (force || now - lastReportedTime > 50 || (totalBytes > 0 && bytesRead >= totalBytes)) {
            lastReportedTime = now
            onProgress(bytesRead, totalBytes)
        }
    }

    override fun close() {
        reportIfNeeded(force = true)
        super.close()
    }
}
