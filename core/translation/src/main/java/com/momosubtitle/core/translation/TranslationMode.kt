package com.momosubtitle.core.translation

/**
 * Translation strategies (docs/MODEL_STRATEGY.md).
 *
 * Milestone 0.1 ships FAST_WHISPER and ORIGINAL_TRANSCRIPT. ADVANCED_LOCAL_NMT
 * (original-language transcription + an offline NLLB-class model) is a Phase 4
 * experiment: it must be benchmarked on-device before it is trusted — it is not
 * guaranteed to beat Whisper's own translate task on a phone CPU.
 */
enum class TranslationMode(
    val id: String,
    val label: String,
    val available: Boolean,
    val note: String
) {
    FAST_WHISPER(
        "translate",
        "English (Whisper translate)",
        true,
        "Whisper's built-in translate task. Best on-device quality/speed trade-off today."
    ),
    ORIGINAL_TRANSCRIPT(
        "transcribe",
        "Original language transcript",
        true,
        "No translation — the model's source-language output. Export alongside English later."
    ),
    ADVANCED_LOCAL_NMT(
        "advanced",
        "Advanced local NMT (Phase 4)",
        false,
        "Planned: transcribe first, then an offline neural MT model. Not an MVP dependency."
    );

    companion object {
        fun byId(id: String): TranslationMode = entries.firstOrNull { it.id == id } ?: FAST_WHISPER
    }
}
