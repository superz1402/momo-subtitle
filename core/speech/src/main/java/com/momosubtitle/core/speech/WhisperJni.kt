package com.momosubtitle.core.speech

/**
 * JNI bridge to the vendored whisper.cpp build (native/whisper, CMake target
 * momo_whisper_jni). Method names must match the C entry points exactly.
 *
 * Thread contract: one WhisperEngine instance owns one context pointer; calls
 * are sequential (the pipeline processes chunks one at a time).
 */
object WhisperJni {

    init {
        System.loadLibrary("momo_whisper_jni")
    }

    /** Loads a GGML model file. Returns a context pointer; 0 means failure. */
    external fun initModel(modelPath: String): Long

    external fun freeModel(ptr: Long)

    /**
     * Runs full inference over a 16 kHz mono s16le WAV file and returns a JSON
     * document: {"language":"ko","durationMs":N,"segments":[{"startMs":..,"endMs":..,"text":".."}]}
     */
    external fun transcribeWav(
        ptr: Long,
        wavPath: String,
        threads: Int,
        translate: Boolean,
        language: String?
    ): String

    /** CPU/backend capability string from whisper.cpp (e.g. NEON/F16C flags). */
    external fun systemInfo(): String
}
