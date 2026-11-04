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

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}

/** Bridge the app module uses to install :core:processing implementations. */
object AppServicesBridge {
    @Volatile
    var jobController: JobController? = null
}
