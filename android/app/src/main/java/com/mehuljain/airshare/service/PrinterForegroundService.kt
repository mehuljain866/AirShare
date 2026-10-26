package com.mehuljain.airshare.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.IBinder
import android.text.format.Formatter
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mehuljain.airshare.R
import com.mehuljain.airshare.http.HttpServer
import com.mehuljain.airshare.printer.IppHttpHandler
import com.mehuljain.airshare.printer.IppPrinterHandler
import com.mehuljain.airshare.printer.JobState
import com.mehuljain.airshare.printer.PrintJob
import com.mehuljain.airshare.transfer.TransferCoordinator
import com.mehuljain.airshare.transfer.TransferPromptActivity
import com.mehuljain.airshare.transfer.TransferReceiver
import com.mehuljain.airshare.transfer.TransferRequest
import com.mehuljain.airshare.transfer.TransferUiState
import com.mehuljain.airshare.ui.MainActivity
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class PrinterForegroundService : Service() {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "airshare-service").apply { isDaemon = true } }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var server: HttpServer? = null
    private var advertiser: NsdAdvertiser? = null
    private var port = PrinterController.config.value.port
    private var jobCount = 0
    private var seenStates: Map<Int, JobState>? = null
    private var timerJob: Job? = null
    @Volatile private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        setupTransferCoordinator()
        scope.launchJobObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // If we were started via startForegroundService, we must still enter foreground first.
                enterForeground()
                worker.execute { teardown(); finish() }
            }
            ACTION_RESTART -> {
                enterForeground()
                worker.execute { teardown(); bringUp() }
            }
            else -> {
                enterForeground()
                worker.execute { if (!running) bringUp() }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "Task removed; service keeps running")
    }

    override fun onDestroy() {
        scope.cancel()
        timerJob?.cancel()
        timerJob = null
        PrinterController.publishRemainingSeconds(null)
        TransferCoordinator.onRequestPrompt = null
        TransferCoordinator.onDismissPrompt = null
        worker.execute { teardown() }
        worker.shutdown()
        val cur = PrinterController.status.value
        if (cur.state != ServiceState.ERROR) {
            PrinterController.publishStatus(cur.copy(state = ServiceState.STOPPED, addresses = emptyList(), error = null))
        }
        super.onDestroy()
    }

    private fun CoroutineScope.launchJobObserver() {
        PrinterController.jobStore(this@PrinterForegroundService).jobs
            .onEach { list ->
                jobCount = list.size
                if (running) notifyUpdate()
                val first = seenStates == null
                val prev = seenStates ?: emptyMap()
                seenStates = list.associate { it.id to it.state }
                if (!first) {
                    list.filter { it.state == JobState.COMPLETED && prev[it.id] != JobState.COMPLETED }
                        .forEach(::notifyJobCompleted)
                }
            }
            .launchIn(this)
    }

    private fun notifyJobCompleted(job: PrintJob) {
        try {
            val contentIntent = if (job.uri != null) {
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse(job.uri), job.format)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                Intent().setComponent(ComponentName(this, MainActivity::class.java))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val pi = PendingIntent.getActivity(
                this, job.id, contentIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val title = job.name.ifBlank { getString(R.string.job_fallback_name, job.id) }
            val text = formatLabel(job.format) + " \u00b7 " + Formatter.formatShortFileSize(this, job.sizeBytes)
            val n = NotificationCompat.Builder(this, JOBS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_airshare)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build()
            getSystemService(NotificationManager::class.java).notify(JOB_NOTIFICATION_BASE + job.id, n)
        } catch (e: Exception) {
            Log.w(TAG, "job notification failed", e)
        }
    }

    private fun formatLabel(mime: String) = when (mime.lowercase()) {
        "application/pdf" -> "PDF"
        "image/jpeg" -> "JPEG"
        "image/png" -> "PNG"
        "image/pwg-raster" -> "PWG Raster"
        "image/urf" -> "URF"
        else -> mime
    }

    private fun bringUp() {
        val cfg = PrinterController.config.value
        port = cfg.port
        PrinterController.publishStatus(PrinterStatus(ServiceState.STARTING, cfg.port))
        notifyUpdate()
        try {
            val jobs = PrinterController.jobStore(this)
            val handler = IppPrinterHandler({ PrinterController.config.value }, jobs)
            val http = IppHttpHandler(handler, { PrinterController.config.value }, jobs) { PrinterIcon.png() }
            val srv = HttpServer(cfg.port, http)
            srv.start()
            server = srv
            advertiser = NsdAdvertiser(this, cfg, srv.boundPort).also { it.register() }
            running = true
            if (cfg.autoOffMinutes > 0) {
                startAutoOffTimer(cfg.autoOffMinutes * 60)
            } else {
                PrinterController.publishRemainingSeconds(null)
            }
            PrinterController.publishStatus(
                PrinterStatus(ServiceState.RUNNING, srv.boundPort, localAddresses(), null)
            )
            notifyUpdate()
            notifyWidgets()
            Log.i(TAG, "Printer running on port ${srv.boundPort}")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start", e)
            teardown()
            PrinterController.publishStatus(
                PrinterStatus(ServiceState.ERROR, cfg.port, emptyList(), e.message ?: e.javaClass.simpleName)
            )
            finish(keepStatus = true)
        }
    }

    private fun startAutoOffTimer(totalSecs: Int) {
        timerJob?.cancel()
        timerJob = scope.launch {
            var remaining = totalSecs
            while (remaining > 0 && running) {
                PrinterController.publishRemainingSeconds(remaining)
                if (remaining % 10 == 0) notifyUpdate()
                delay(1000L)
                remaining--
            }
            if (running) {
                while (TransferCoordinator.uiState.value is TransferUiState.Receiving) {
                    delay(1000L)
                }
                Log.i(TAG, "Auto-off timer expired; stopping service")
                worker.execute { teardown(); finish() }
            }
        }
    }

    private fun teardown() {
        running = false
        timerJob?.cancel()
        timerJob = null
        PrinterController.publishRemainingSeconds(null)
        notifyWidgets()
        try { advertiser?.unregister() } catch (e: Throwable) { Log.w(TAG, "nsd stop", e) }
        advertiser = null
        try { server?.stop() } catch (e: Throwable) { Log.w(TAG, "server stop", e) }
        server = null
    }

    private fun finish(keepStatus: Boolean = false) {
        if (!keepStatus) {
            PrinterController.publishStatus(PrinterStatus(ServiceState.STOPPED, port))
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun enterForeground() {
        val n = buildNotification()
        startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
    }

    private fun notifyUpdate() {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.w(TAG, "notify failed", e)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent().setComponent(ComponentName(this, com.mehuljain.airshare.ui.MainActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, PrinterForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val remaining = PrinterController.remainingSeconds.value
        val text = if (remaining != null && remaining > 0) {
            val m = remaining / 60
            val s = remaining % 60
            String.format(Locale.getDefault(), "Ready · Auto-off in %02d:%02d", m, s)
        } else {
            getString(R.string.notification_jobs, jobCount)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_airshare)
            .setContentTitle(getString(R.string.notification_title, port))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.notification_stop), stop)
            .build()
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(ch)
        nm.createNotificationChannel(
            NotificationChannel(JOBS_CHANNEL_ID, getString(R.string.notification_channel_jobs), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val incomingCh = NotificationChannel(
            INCOMING_CHANNEL_ID,
            "Incoming transfers",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "AirShare incoming transfer alerts"
            enableVibration(true)
        }
        nm.createNotificationChannel(incomingCh)
    }

    private fun setupTransferCoordinator() {
        TransferCoordinator.onRequestPrompt = { req ->
            showTransferPromptNotification(req)
        }

        TransferCoordinator.onDismissPrompt = {
            dismissTransferPromptNotification()
        }

        TransferCoordinator.uiState
            .onEach { state ->
                when (state) {
                    is TransferUiState.Receiving -> {
                        updateTransferProgressNotification(state.request, state.bytesRead, state.totalBytes, state.progress)
                    }
                    is TransferUiState.Completed -> {
                        dismissTransferPromptNotification()
                        showTransferCompletedNotification(state)
                    }
                    else -> {}
                }
            }
            .launchIn(scope)
    }

    private fun showTransferPromptNotification(req: TransferRequest) {
        val acceptPi = PendingIntent.getBroadcast(
            this, 101,
            Intent(this, TransferReceiver::class.java).setAction(TransferReceiver.ACTION_ACCEPT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val declinePi = PendingIntent.getBroadcast(
            this, 102,
            Intent(this, TransferReceiver::class.java).setAction(TransferReceiver.ACTION_DECLINE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val fullScreenPi = PendingIntent.getActivity(
            this, 103,
            Intent(this, TransferPromptActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val largeIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

        val n = NotificationCompat.Builder(this, INCOMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_airshare)
            .setLargeIcon(largeIcon)
            .setContentTitle("AirShare")
            .setContentText(req.promptSubtitle)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPi, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .addAction(0, "Decline", declinePi)
            .addAction(0, "Accept", acceptPi)
            .build()

        getSystemService(NotificationManager::class.java).notify(PROMPT_NOTIFICATION_ID, n)
    }

    private fun updateTransferProgressNotification(req: TransferRequest, bytesRead: Long, totalBytes: Long, progress: Float) {
        val max = if (totalBytes > 0) 100 else 0
        val curr = if (totalBytes > 0) (progress * 100).toInt() else 0
        val text = if (totalBytes > 0) {
            "Receiving ${curr}% · ${Formatter.formatShortFileSize(this, bytesRead)} / ${Formatter.formatShortFileSize(this, totalBytes)}"
        } else {
            "Receiving ${Formatter.formatShortFileSize(this, bytesRead)}"
        }

        val largeIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

        val n = NotificationCompat.Builder(this, INCOMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_airshare)
            .setLargeIcon(largeIcon)
            .setContentTitle("AirShare")
            .setContentText(text)
            .setProgress(max, curr, totalBytes <= 0)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        getSystemService(NotificationManager::class.java).notify(PROMPT_NOTIFICATION_ID, n)
    }

    private fun showTransferCompletedNotification(state: TransferUiState.Completed) {
        val req = state.request
        val parsed = req.parsedInfo
        val dest = if (parsed.isPhoto) "Saved to Gallery" else "Saved to Documents"
        val title = if (state.success) "Transfer complete" else "Transfer declined"
        val text = if (state.success) "${parsed.contentDisplayName} · $dest" else state.message

        val contentIntent = PendingIntent.getActivity(
            this, req.jobId,
            Intent().setComponent(ComponentName(this, MainActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, JOBS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_airshare)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (state.thumbnail != null) {
            builder.setLargeIcon(state.thumbnail)
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(state.thumbnail).showBigPictureWhenCollapsed(false))
        } else {
            builder.setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
        }

        getSystemService(NotificationManager::class.java).notify(JOB_NOTIFICATION_BASE + req.jobId, builder.build())
    }

    private fun notifyWidgets() {
        try {
            com.mehuljain.airshare.widget.AirShareWidgetProvider.updateAll(this)
            com.mehuljain.airshare.widget.AirShareTileService.updateTileState(this)
        } catch (_: Throwable) {}
    }

    private fun dismissTransferPromptNotification() {
        getSystemService(NotificationManager::class.java).cancel(PROMPT_NOTIFICATION_ID)
    }

    private fun localAddresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            .mapNotNull { it.hostAddress }
            .distinct()
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        const val ACTION_START = "com.mehuljain.airshare.action.START"
        const val ACTION_STOP = "com.mehuljain.airshare.action.STOP"
        const val ACTION_RESTART = "com.mehuljain.airshare.action.RESTART"
        private const val CHANNEL_ID = "printer"
        private const val JOBS_CHANNEL_ID = "jobs"
        const val INCOMING_CHANNEL_ID = "incoming_transfers"
        private const val JOB_NOTIFICATION_BASE = 1000
        private const val NOTIFICATION_ID = 1
        private const val PROMPT_NOTIFICATION_ID = 2
        private const val TAG = "AirShare"
    }
}
