package com.momosubtitle.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.momosubtitle.app.MainActivity
import com.momosubtitle.app.R
import com.momosubtitle.core.common.Graph
import com.momosubtitle.core.common.job.RunStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service (issue: "background eligible + progress notification").
 *
 * Keeps transcription alive while the app is in the background and mirrors
 * pipeline progress into an ongoing notification: per-chunk percentage,
 * current stage, and pause/resume/cancel actions. The pipeline itself lives
 * in TranscriptionJobManager (:core:processing) and stays checkpointed —
 * this service only supervises, notifies, and routes control actions.
 */
class TranscriptionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val active = LinkedHashSet<String>()
    private var observer: Job? = null
    private var shownRunId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Subtitle transcription",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progress of offline subtitle transcription"
                setShowBadge(false)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val runId = intent?.getStringExtra(EXTRA_RUN_ID)
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> {
                if (runId != null) {
                    active += runId
                    shownRunId = runId
                }
                promote()
                observe()
            }
            ACTION_RESUME -> runId?.let {
                active += it
                shownRunId = it
                Graph.jobController.resume(it)
                promote()
                observe()
            }
            ACTION_PAUSE -> runId?.let { Graph.jobController.pause(it) }
            ACTION_CANCEL -> runId?.let { Graph.jobController.cancel(it) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun promote() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(null, null, 0, 0, false),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(null, null, 0, 0, false))
        }
    }

    /** Collect run + project rows for the most recent run and refresh the notification. */
    private fun observe() {
        observer?.cancel()
        val runId = shownRunId ?: return
        observer = scope.launch {
            combine(
                Graph.database.runDao().observe(runId),
                Graph.database.projectDao().observeAll()
            ) { run, projects -> run to projects.firstOrNull { it.id == run?.projectId } }
                .collect { (run, project) ->
                    if (run == null) {
                        finishRun(runId)
                        return@collect
                    }
                    val status = RunStatus.safe(run.status)
                    val name = project?.name ?: "Subtitle project"
                    when (status) {
                        RunStatus.DONE, RunStatus.PARTIAL, RunStatus.FAILED, RunStatus.CANCELLED -> {
                            notifyFinal(name, run.doneChunks, run.totalChunks, status)
                            finishRun(runId)
                        }
                        else -> {
                            shownRunId = runId
                            val manager = getSystemService(NotificationManager::class.java)
                            manager.notify(
                                NOTIFICATION_ID,
                                buildNotification(
                                    name,
                                    run.stage,
                                    run.doneChunks,
                                    run.totalChunks,
                                    status == RunStatus.PAUSED
                                )
                            )
                        }
                    }
                }
        }
    }

    private fun finishRun(runId: String) {
        active.remove(runId)
        if (active.isEmpty()) {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        } else {
            shownRunId = active.lastOrNull()
            observe()
        }
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun actionIntent(action: String, runId: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, TranscriptionService::class.java).setAction(action).putExtra(EXTRA_RUN_ID, runId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildNotification(
        name: String?,
        stage: String?,
        done: Int,
        total: Int,
        paused: Boolean
    ): Notification {
        val percent = if (total > 0) (done * 100 / total) else 0
        val runId = shownRunId.orEmpty()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent())
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
        if (name != null) builder.setContentTitle(name)
        builder.setContentText(
            when {
                stage == null -> "Preparing…"
                paused -> "Paused — $percent% · $stage"
                else -> "$percent% · $stage"
            }
        )
        if (total > 0) builder.setProgress(100, percent, false)
        if (runId.isNotEmpty()) {
            if (paused) {
                builder.addAction(
                    0, "Resume",
                    actionIntent(ACTION_RESUME, runId, REQUEST_RESUME)
                )
            } else {
                builder.addAction(
                    0, "Pause",
                    actionIntent(ACTION_PAUSE, runId, REQUEST_PAUSE)
                )
            }
            builder.addAction(
                0, "Cancel",
                actionIntent(ACTION_CANCEL, runId, REQUEST_CANCEL)
            )
        }
        return builder.build()
    }

    private fun notifyFinal(name: String, done: Int, total: Int, status: RunStatus) {
        val (title, text) = when (status) {
            RunStatus.DONE -> "Subtitles ready" to "$name · $total chunks complete"
            RunStatus.PARTIAL -> "Subtitles mostly ready" to
                "$name · $done/$total chunks — resume to retry failed ones"
            RunStatus.FAILED -> "Transcription failed" to name
            else -> "Transcription cancelled" to name
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID + 1, builder.build())
    }

    companion object {
        private const val CHANNEL_ID = "transcription"
        private const val NOTIFICATION_ID = 41
        private const val REQUEST_PAUSE = 1
        private const val REQUEST_RESUME = 2
        private const val REQUEST_CANCEL = 3
        private const val EXTRA_RUN_ID = "runId"
        const val ACTION_START = "com.momosubtitle.action.START"
        const val ACTION_PAUSE = "com.momosubtitle.action.PAUSE"
        const val ACTION_RESUME = "com.momosubtitle.action.RESUME"
        const val ACTION_CANCEL = "com.momosubtitle.action.CANCEL"

        /** Begin observing a freshly created run in the foreground service. */
        fun start(context: Context, runId: String) {
            val intent = Intent(context, TranscriptionService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RUN_ID, runId)
            context.startForegroundService(intent)
        }

        fun pause(context: Context, runId: String) = send(context, ACTION_PAUSE, runId)
        fun resume(context: Context, runId: String) = send(context, ACTION_RESUME, runId)
        fun cancel(context: Context, runId: String) = send(context, ACTION_CANCEL, runId)

        private fun send(context: Context, action: String, runId: String) {
            context.startService(
                Intent(context, TranscriptionService::class.java)
                    .setAction(action)
                    .putExtra(EXTRA_RUN_ID, runId)
            )
        }
    }
}
