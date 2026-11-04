package com.momosubtitle.app

import android.app.Application
import com.momosubtitle.core.common.AppServicesBridge
import com.momosubtitle.core.common.Graph
import com.momosubtitle.core.processing.TranscriptionJobManager

class MomoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        // The job controller is implemented in :core:processing and installed here so that
        // feature modules depend only on the stable interface in :core:common.
        AppServicesBridge.jobController = TranscriptionJobManager(
            appContext = Graph.appContext,
            database = Graph.database,
            settings = Graph.settings
        )
    }
}
