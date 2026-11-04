package com.momosubtitle.core.speech

import kotlinx.serialization.json.Json
import java.io.File

/**
 * Kotlin-facing whisper.cpp wrapper. The model is opened once per job and kept
 * in memory across all chunks — reloading a 57 MB model per 30-second chunk
 * would destroy the real-time factor.
 */
class WhisperEngine {

    private var ctxPtr = 0L

    val isLoaded: Boolean get() = ctxPtr != 0L

    fun open(modelPath: String) {
        check(ctxPtr == 0L) { "WhisperEngine already has a model loaded" }
        val ptr = WhisperJni.initModel(modelPath)
        require(ptr != 0L) { "Failed to load Whisper model at $modelPath" }
        ctxPtr = ptr
    }

    fun transcribe(wavPath: String, threads: Int, translate: Boolean, language: String): NativeResult {
        check(ctxPtr != 0L) { "Model not loaded — call open() first" }
        val json = WhisperJni.transcribeWav(ctxPtr, wavPath, threads, translate, language)
        return json.decodeFromString(NativeResult.serializer(), json)
    }

    fun close() {
        if (ctxPtr != 0L) {
            WhisperJni.freeModel(ctxPtr)
            ctxPtr = 0
        }
        runCatching { File(tempDirMarker()).delete() }
    }

    fun systemInfo(): String = runCatching { WhisperJni.systemInfo() }.getOrDefault("unavailable")

    private fun tempDirMarker(): String = "unused"

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
