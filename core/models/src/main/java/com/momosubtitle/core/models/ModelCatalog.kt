package com.momosubtitle.core.models

/**
 * Downloadable Whisper GGML models (docs/MODEL_STRATEGY.md).
 *
 * Sizes and SHA-256 digests below were fetched from the HuggingFace repo
 * ggerganov/whisper.cpp and MUST be updated together with the pinned release.
 * Weights are never bundled in the APK and never committed to Git.
 */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val tier: String,
    val description: String
)

object ModelCatalog {

    private const val BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"

    val TINY_Q5_1 = ModelSpec(
        id = "tiny-q5_1",
        displayName = "Tiny multilingual Q5_1",
        fileName = "ggml-tiny-q5_1.bin",
        url = BASE_URL + "ggml-tiny-q5_1.bin",
        sizeBytes = 32_152_673,
        sha256 = "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7",
        tier = "Fast fallback",
        description = "Quick previews and low-resource devices. Rough transcripts only."
    )

    val BASE_Q5_1 = ModelSpec(
        id = "base-q5_1",
        displayName = "Base multilingual Q5_1",
        fileName = "ggml-base-q5_1.bin",
        url = BASE_URL + "ggml-base-q5_1.bin",
        sizeBytes = 59_707_625,
        sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
        tier = "Default",
        description = "Initial target for the Moto G85 5G. Balance of accuracy, RAM and speed."
    )

    val SMALL_Q5_1 = ModelSpec(
        id = "small-q5_1",
        displayName = "Small multilingual Q5_1",
        fileName = "ggml-small-q5_1.bin",
        url = BASE_URL + "ggml-small-q5_1.bin",
        sizeBytes = 190_085_487,
        sha256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
        tier = "Accuracy",
        description = "Difficult interviews and mixed languages. Benchmark before enabling by default."
    )

    val all: List<ModelSpec> = listOf(TINY_Q5_1, BASE_Q5_1, SMALL_Q5_1)

    fun byId(id: String): ModelSpec? = all.firstOrNull { it.id == id }
}
