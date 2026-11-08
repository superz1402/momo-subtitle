package com.momosubtitle.core.common

import android.content.Context
import com.momosubtitle.core.common.job.JobController
import com.momosubtitle.core.database.AppDatabase
import com.momosubtitle.core.storage.MomoPaths

/**
 * Process-wide service locator. Deliberately tiny: feature modules consume the
 * stable contracts here (:core:common) while the app module installs the
 * concrete implementations at startup (see MomoApp).
 */
object Graph {
    lateinit var appContext: Context
        private set

    val paths: MomoPaths by lazy { MomoPaths(appContext) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val jobController: JobController
        get() = checkNotNull(AppServicesBridge.jobController) {
            "JobController not installed — the app module must set AppServicesBridge.jobController at startup"
        }

    val transcription: AppServicesBridge.TranscriptionRunner
        get() = checkNotNull(AppServicesBridge.transcription) {
            "TranscriptionRunner not installed — the app module must set AppServicesBridge.transcription at startup"
        }

    val modelDownload: AppServicesBridge.ModelDownloadRunner
        get() = checkNotNull(AppServicesBridge.modelDownload) {
            "ModelDownloadRunner not installed — the app module must set AppServicesBridge.modelDownload at startup"
        }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}

/** Bridge the app module uses to install :core:processing implementations. */
object AppServicesBridge {
    @Volatile
    var jobController: JobController? = null

    /**
     * Foreground-service entry points, installed by the app module. Feature
     * screens route run control (and background-eligibility) through this so
     * the on-screen controls and the notification stay in sync.
     */
    interface TranscriptionRunner {
        fun start(context: Context, runId: String)
        fun pause(context: Context, runId: String)
        fun resume(context: Context, runId: String)
        fun cancel(context: Context, runId: String)
    }

    @Volatile
    var transcription: TranscriptionRunner? = null

    /**
     * Foreground-service entry points for background model downloads, installed
     * by the app module. Screens only send start/cancel intents here and observe
     * ModelRepository.downloads — heavy work never runs in the caller.
     */
    interface ModelDownloadRunner {
        fun start(context: Context, specId: String)
        fun cancel(context: Context, specId: String)
    }

    @Volatile
    var modelDownload: ModelDownloadRunner? = null
}
