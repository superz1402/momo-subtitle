package com.momosubtitle.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.momosubtitle.core.common.Graph
import com.momosubtitle.core.common.TimeFormat
import com.momosubtitle.core.models.DownloadProgress
import com.momosubtitle.core.models.ModelCatalog
import com.momosubtitle.core.models.ModelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that owns model downloads so they keep running while the
 * app is backgrounded, with live progress in the notification bar — the same
 * isolation model as [TranscriptionService]: heavy network I/O happens on this
 * service's own coroutine scope (Dispatchers.IO inside the repository), never
 * on the UI thread or in a ViewModel scope. The screen only sends start/cancel
 * intents and observes [ModelRepository.downloads].
 *
 * START_REDELIVER_INTENT: if the system kills the process mid-download, the
 * last start intent is re-delivered and the download resumes from the .part
 * file instead of dying silently.
 */
class ModelDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var downloadJob: Job? = null
    private var observerJob: Job? = null
    private var activeSpecId: String? = null
    private var lastNotifyAt: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Model downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progress of model downloads, continued in the background"
                setShowBadge(false)
            }
        )
        observerJob = scope.launch {
            val repo = ModelRepository.shared(applicationContext, Graph.settings)
            repo.downloads.collect { map ->
                val id = activeSpecId ?: return@collect
                val progress = map[id] ?: return@collect
                if (!progress.done && progress.error == null) {
                    notifyProgress(id, progress)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val specId = intent?.getStringExtra(EXTRA_MODEL_ID)
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> {
                if (specId != null) {
                    promote()
                    startJob(specId)
                }
            }
            ACTION_CANCEL -> specId?.let {
                if (it == activeSpecId) {
                    downloadJob?.cancel()
                    downloadJob = null
                    activeSpecId = null
                    stopIfIdle()
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun promote() {
        val notification = buildNotification(
            title = null,
            text = "Preparing…",
            percent = 0,
            cancellable = false
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** Single-flight: a new start replaces any running download. */
    private fun startJob(specId: String) {
        if (downloadJob?.isActive == true) downloadJob?.cancel()
        activeSpecId = specId
        downloadJob = scope.launch {
            val repo = ModelRepository.shared(applicationContext, Graph.settings)
            val spec = ModelCatalog.byId(specId)
            try {
                if (spec != null) {
                    repo.download(spec)
                    notifyFinal(
                        title = "${spec.displayName} ready",
                        text = "Installed and verified. Tap to open Momo Subtitle.",
                        error = false
                    )
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                // User cancelled; the .part file stays for the next resume.
                throw ce
            } catch (t: Throwable) {
                notifyFinal(
                    title = "Download failed",
                    text = t.message ?: "Model download could not be completed.",
                    error = true
                )
            } finally {
                if (activeSpecId == specId) {
                    activeSpecId = null
                    downloadJob = null
                }
                stopIfIdle()
            }
        }
    }

    private fun stopIfIdle() {
        if (activeSpecId == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun notifyProgress(specId: String, progress: DownloadProgress) {
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt < NOTIFY_INTERVAL_MS) return
        lastNotifyAt = now
        val spec = ModelCatalog.byId(specId) ?: return
        val percent = if (progress.totalBytes > 0) {
            (progress.downloadedBytes * 100 / progress.totalBytes).toInt().coerceIn(0, 100)
        } else 0
        val eta = if (progress.speedBps > 0) {
            val seconds = (progress.totalBytes - progress.downloadedBytes) / progress.speedBps
            " · ~${humanSeconds(seconds)}"
        } else ""
        val notification = buildNotification(
            title = spec.displayName,
            text = "$percent% · ${TimeFormat.humanBytes(progress.downloadedBytes)} / " +
                "${TimeFormat.humanBytes(progress.totalBytes)}$eta",
            percent = percent,
            cancellable = true
        )
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun notifyFinal(title: String, text: String, error: Boolean) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (error) android.R.drawable.stat_notify_error else android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
        getSystemService(NotificationManager::class.java).notify(FINAL_NOTIFICATION_ID, builder.build())
    }

    private fun buildNotification(
        title: String?,
        text: String,
        percent: Int,
        cancellable: Boolean
    ): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(contentIntent())
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
        if (title != null) builder.setContentTitle(title)
        builder.setContentText(text)
        builder.setProgress(100, percent, false)
        val specId = activeSpecId
        if (cancellable && specId != null) {
            builder.addAction(
                0,
                "Cancel",
                sendIntent(ACTION_CANCEL, specId, REQUEST_CANCEL)
            )
        }
        return builder.build()
    }

    private fun contentIntent() = android.app.PendingIntent.getActivity(
        this,
        0,
        packageManager.getLaunchIntentForPackage(packageName),
        android.app.PendingIntent.FLAG_IMMUTABLE
    )

    private fun sendIntent(action: String, specId: String, requestCode: Int) =
        android.app.PendingIntent.getService(
            this,
            requestCode,
            Intent(this, ModelDownloadService::class.java)
                .setAction(action)
                .putExtra(EXTRA_MODEL_ID, specId),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

    private fun humanSeconds(seconds: Long): String = when {
        seconds < 5 -> "seconds"
        seconds < 60 -> "${seconds}s left"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s left"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 42
        private const val FINAL_NOTIFICATION_ID = 43
        private const val REQUEST_CANCEL = 4
        private const val NOTIFY_INTERVAL_MS = 500L
        private const val EXTRA_MODEL_ID = "modelId"
        const val ACTION_START = "com.momosubtitle.action.DOWNLOAD_START"
        const val ACTION_CANCEL = "com.momosubtitle.action.DOWNLOAD_CANCEL"

        /** Begin (or resume) a background model download with notification progress. */
        fun start(context: android.content.Context, specId: String) {
            context.startForegroundService(
                Intent(context, ModelDownloadService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_MODEL_ID, specId)
            )
        }

        fun cancel(context: android.content.Context, specId: String) {
            context.startService(
                Intent(context, ModelDownloadService::class.java)
                    .setAction(ACTION_CANCEL)
                    .putExtra(EXTRA_MODEL_ID, specId)
            )
        }
    }
}
