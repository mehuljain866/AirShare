package com.mehuljain.airshare.transfer

import android.graphics.Bitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

data class TransferRequest(
    val jobId: Int,
    val senderName: String,
    val jobName: String,
    val format: String,
    val totalBytes: Long = 0,
    val userAgent: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val batchIndex: Int = 1,
) {
    val parsedInfo: DeviceInfoParser.ParsedInfo get() = DeviceInfoParser.parse(userAgent, senderName, jobName, format)
    val promptSubtitle: String get() = "${parsedInfo.deviceDisplayName} would like to share ${parsedInfo.contentDisplayName}"
}

sealed class TransferUiState {
    object Idle : TransferUiState()
    data class Prompt(
        val request: TransferRequest,
        val thumbnail: Bitmap? = null,
    ) : TransferUiState()
    data class Receiving(
        val request: TransferRequest,
        val bytesRead: Long,
        val totalBytes: Long,
        val progress: Float, // 0.0 to 1.0, or -1f if total length is unknown
        val thumbnail: Bitmap? = null,
    ) : TransferUiState()
    data class Completed(
        val request: TransferRequest,
        val success: Boolean,
        val message: String,
        val thumbnail: Bitmap? = null,
    ) : TransferUiState()
}

object TransferCoordinator {
    private val _uiState = MutableStateFlow<TransferUiState>(TransferUiState.Idle)
    val uiState: StateFlow<TransferUiState> = _uiState

    @Volatile
    private var pendingDeferred: CompletableDeferred<Boolean>? = null

    @Volatile
    private var activeRequest: TransferRequest? = null

    var onRequestPrompt: ((TransferRequest) -> Unit)? = null
    var onDismissPrompt: (() -> Unit)? = null

    private val batchLock = Any()
    @Volatile private var authorizedBatchSender: String? = null
    @Volatile private var batchExpiresAtMs: Long = 0L
    @Volatile private var batchCount: Int = 0
    @Volatile private var declinedBatchSender: String? = null
    @Volatile private var declinedExpiresAtMs: Long = 0L
    private val jobUserAgents = java.util.concurrent.ConcurrentHashMap<Int, String>()
    @Volatile private var lastKnownUserAgent: String? = null

    fun recordUserAgent(jobId: Int, userAgent: String?) {
        if (!userAgent.isNullOrBlank()) {
            jobUserAgents[jobId] = userAgent
            lastKnownUserAgent = userAgent
        }
    }

    fun getUserAgent(jobId: Int): String? = jobUserAgents[jobId] ?: lastKnownUserAgent

    /**
     * Called on connection thread in IppPrinterHandler.
     * Blocks until user accepts, declines, or timeout (45s).
     * If the user already accepted an item from this sender within the last 30s,
     * subsequent items in the batch are automatically accepted without prompting again.
     */
    fun awaitApproval(
        jobId: Int,
        senderName: String,
        jobName: String,
        format: String,
        totalBytes: Long,
        userAgent: String? = null,
    ): Boolean {
        recordUserAgent(jobId, userAgent)
        val senderKey = "${senderName}@@${userAgent.orEmpty()}"
        val now = System.currentTimeMillis()

        val isBatchAutoApproved = synchronized(batchLock) {
            if (declinedBatchSender == senderKey && now < declinedExpiresAtMs) {
                return false
            }
            if (authorizedBatchSender == senderKey && now < batchExpiresAtMs) {
                batchExpiresAtMs = now + 30_000L
                batchCount++
                true
            } else {
                false
            }
        }

        if (isBatchAutoApproved) {
            val req = TransferRequest(
                jobId = jobId,
                senderName = senderName,
                jobName = jobName,
                format = format,
                totalBytes = totalBytes,
                userAgent = userAgent,
                batchIndex = batchCount,
            )
            activeRequest = req
            return true
        }

        val req = TransferRequest(
            jobId = jobId,
            senderName = senderName,
            jobName = jobName,
            format = format,
            totalBytes = totalBytes,
            userAgent = userAgent,
            batchIndex = 1,
        )
        activeRequest = req
        val deferred = CompletableDeferred<Boolean>()
        pendingDeferred = deferred

        _uiState.value = TransferUiState.Prompt(req)
        onRequestPrompt?.invoke(req)

        val accepted = runBlocking {
            withTimeoutOrNull(45_000L) {
                deferred.await()
            } ?: false
        }

        pendingDeferred = null
        if (!accepted) {
            synchronized(batchLock) {
                declinedBatchSender = senderKey
                declinedExpiresAtMs = System.currentTimeMillis() + 20_000L
                authorizedBatchSender = null
            }
            _uiState.value = TransferUiState.Completed(req, success = false, message = "Declined")
            onDismissPrompt?.invoke()
            activeRequest = null
        } else {
            synchronized(batchLock) {
                authorizedBatchSender = senderKey
                batchExpiresAtMs = System.currentTimeMillis() + 30_000L
                batchCount = 1
                declinedBatchSender = null
            }
        }
        return accepted
    }

    fun startReceiving(
        jobId: Int,
        senderName: String,
        jobName: String,
        format: String,
        totalBytes: Long,
        userAgent: String? = null,
    ) {
        recordUserAgent(jobId, userAgent)
        val req = activeRequest ?: TransferRequest(
            jobId = jobId,
            senderName = senderName,
            jobName = jobName,
            format = format,
            totalBytes = totalBytes,
            userAgent = userAgent,
            batchIndex = maxOf(1, batchCount),
        )
        activeRequest = req
        val currentThumb = (_uiState.value as? TransferUiState.Prompt)?.thumbnail
        _uiState.value = TransferUiState.Receiving(
            request = req,
            bytesRead = 0,
            totalBytes = totalBytes,
            progress = 0f,
            thumbnail = currentThumb,
        )
    }

    fun updateProgress(jobId: Int, bytesRead: Long, totalBytes: Long) {
        val req = activeRequest ?: return
        val currentThumb = when (val s = _uiState.value) {
            is TransferUiState.Prompt -> s.thumbnail
            is TransferUiState.Receiving -> s.thumbnail
            else -> null
        }
        val progress = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else -1f
        _uiState.value = TransferUiState.Receiving(
            request = req,
            bytesRead = bytesRead,
            totalBytes = totalBytes,
            progress = progress,
            thumbnail = currentThumb,
        )
    }

    fun setThumbnail(jobId: Int, bitmap: Bitmap) {
        val cur = _uiState.value
        when (cur) {
            is TransferUiState.Prompt -> _uiState.value = cur.copy(thumbnail = bitmap)
            is TransferUiState.Receiving -> _uiState.value = cur.copy(thumbnail = bitmap)
            is TransferUiState.Completed -> _uiState.value = cur.copy(thumbnail = bitmap)
            else -> {}
        }
    }

    fun finishTransfer(jobId: Int, success: Boolean, message: String = if (success) "Saved" else "Failed") {
        synchronized(batchLock) {
            if (success) {
                batchExpiresAtMs = System.currentTimeMillis() + 30_000L
            }
        }
        val req = activeRequest ?: return
        val currentThumb = when (val s = _uiState.value) {
            is TransferUiState.Receiving -> s.thumbnail
            is TransferUiState.Prompt -> s.thumbnail
            else -> null
        }
        _uiState.value = TransferUiState.Completed(req, success, message, currentThumb)
        activeRequest = null
    }

    fun accept() {
        synchronized(batchLock) {
            activeRequest?.let { req ->
                authorizedBatchSender = "${req.senderName}@@${req.userAgent.orEmpty()}"
                batchExpiresAtMs = System.currentTimeMillis() + 30_000L
                batchCount = 1
                declinedBatchSender = null
            }
        }
        pendingDeferred?.complete(true)
    }

    fun decline() {
        synchronized(batchLock) {
            activeRequest?.let { req ->
                declinedBatchSender = "${req.senderName}@@${req.userAgent.orEmpty()}"
                declinedExpiresAtMs = System.currentTimeMillis() + 20_000L
                authorizedBatchSender = null
            }
        }
        pendingDeferred?.complete(false)
    }

    fun reset() {
        synchronized(batchLock) {
            authorizedBatchSender = null
            batchExpiresAtMs = 0L
            batchCount = 0
            declinedBatchSender = null
            declinedExpiresAtMs = 0L
            jobUserAgents.clear()
            lastKnownUserAgent = null
        }
        _uiState.value = TransferUiState.Idle
        onDismissPrompt?.invoke()
        activeRequest = null
        pendingDeferred?.complete(false)
        pendingDeferred = null
    }

    private fun cleanSenderName(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isEmpty() || trimmed.equals("anonymous", ignoreCase = true)) {
            "Nearby Device"
        } else {
            trimmed
        }
    }

    private fun cleanJobName(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isEmpty() || trimmed.equals("Untitled", ignoreCase = true)) {
            "photo or document"
        } else {
            trimmed
        }
    }
}
