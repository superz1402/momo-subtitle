package com.momosubtitle.core.storage

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * On-disk layout (docs/PROCESSING_PIPELINE.md):
 *
 * files/MomoSubtitle/
 *   models/whisper/*.bin
 *   projects/{projectId}/audio/extracted.wav
 *   projects/{projectId}/chunks/chunk_0001.wav ...
 *   projects/{projectId}/exports/
 */
class MomoPaths(private val context: Context) {

    val root: File get() = File(context.filesDir, "MomoSubtitle").apply { mkdirs() }

    val modelsDir: File get() = File(root, "models/whisper").apply { mkdirs() }

    fun projectDir(projectId: String): File = File(root, "projects/$projectId").apply { mkdirs() }

    fun audioDir(projectId: String): File = File(projectDir(projectId), "audio").apply { mkdirs() }

    fun chunksDir(projectId: String): File = File(projectDir(projectId), "chunks").apply { mkdirs() }

    fun subtitlesDir(projectId: String): File = File(projectDir(projectId), "subtitles").apply { mkdirs() }

    fun exportsDir(projectId: String): File = File(projectDir(projectId), "exports").apply { mkdirs() }

    fun extractedWav(projectId: String): File = File(audioDir(projectId), "extracted.wav")

    fun chunkWav(projectId: String, index: Int): File =
        File(chunksDir(projectId), "chunk_%04d.wav".format(index))

    fun dirSize(dir: File): Long =
        dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    companion object {
        fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)
    }
}
