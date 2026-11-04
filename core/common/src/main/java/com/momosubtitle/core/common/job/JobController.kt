package com.momosubtitle.core.common.job

/** One import request, as created by the import screen. */
data class ImportRequest(
    val mediaUri: String,
    val displayName: String,
    val modelId: String,
    /** "translate" = Whisper speech → English; "transcribe" = original language. */
    val mode: String,
    /** ISO code ("ko", "zh", "th", "ja", "en") or "auto". */
    val language: String,
    val chunkSeconds: Int
)

enum class RunStatus {
    QUEUED, EXTRACTING, TRANSCRIBING, BUILDING,
    DONE, PARTIAL, FAILED, CANCELLED, PAUSED;

    companion object {
        fun safe(value: String?): RunStatus? = runCatching { valueOf(value ?: return null) }.getOrNull()
    }
}

enum class ChunkStatus { PENDING, RUNNING, DONE, FAILED }

/** Implemented by :core:processing; installed by the app module. */
interface JobController {
    suspend fun createAndStartRun(request: ImportRequest): String
    fun start(runId: String)
    fun pause(runId: String)
    fun cancel(runId: String)
    fun resume(runId: String)
}
