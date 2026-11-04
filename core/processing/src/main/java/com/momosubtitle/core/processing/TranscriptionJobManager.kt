package com.momosubtitle.core.processing

import android.content.Context
import android.net.Uri
import com.momosubtitle.core.common.SettingsRepository
import com.momosubtitle.core.common.job.ChunkStatus
import com.momosubtitle.core.common.job.ImportRequest
import com.momosubtitle.core.common.job.JobController
import com.momosubtitle.core.common.job.RunStatus
import com.momosubtitle.core.database.AppDatabase
import com.momosubtitle.core.database.ChunkEntity
import com.momosubtitle.core.database.ProjectEntity
import com.momosubtitle.core.database.SegmentEntity
import com.momosubtitle.core.database.TranscriptionRunEntity
import com.momosubtitle.core.media.AudioExtractor
import com.momosubtitle.core.media.WavFile
import com.momosubtitle.core.models.ModelCatalog
import com.momosubtitle.core.models.ModelRepository
import com.momosubtitle.core.speech.WhisperEngine
import com.momosubtitle.core.storage.MomoPaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Pipeline orchestration (docs/PROCESSING_PIPELINE.md):
 *
 *   extract -> plan chunks -> load model -> per-chunk transcribe (checkpoint
 *   after every chunk) -> renumber segments -> DONE/PARTIAL/FAILED.
 *
 * Rules encoded here:
 *  - Never process a long interview as one giant audio operation.
 *  - Never silently discard unprocessed audio: failed chunks are recorded and
 *    the run ends PARTIAL, resumable.
 *  - Model is opened once per job, closed in a finally block.
 */
class TranscriptionJobManager(
    private val appContext: Context,
    private val database: AppDatabase,
    private val settings: SettingsRepository
) : JobController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = ConcurrentHashMap<String, Job>()

    private val paths by lazy { MomoPaths(appContext) }
    private val modelRepository by lazy { ModelRepository(appContext, settings) }
    private val audioExtractor by lazy { AudioExtractor(appContext) }

    override suspend fun createAndStartRun(request: ImportRequest): String {
        val projectId = MomoPaths.newId()
        val runId = MomoPaths.newId()
        val now = System.currentTimeMillis()
        database.projectDao().insert(
            ProjectEntity(
                id = projectId,
                name = request.displayName,
                mediaUri = request.mediaUri,
                mediaName = request.displayName,
                durationSec = 0.0,
                createdAt = now
            )
        )
        database.runDao().insert(
            TranscriptionRunEntity(
                id = runId,
                projectId = projectId,
                modelId = request.modelId,
                mode = request.mode,
                language = request.language,
                chunkSeconds = request.chunkSeconds,
                status = RunStatus.QUEUED.name,
                stage = "Queued",
                totalChunks = 0,
                doneChunks = 0,
                detectedLanguage = null,
                errorMessage = null,
                createdAt = now,
                updatedAt = now
            )
        )
        start(runId)
        return runId
    }

    override fun start(runId: String) {
        check(!jobs.containsKey(runId)) { "Run $runId is already active" }
        jobs[runId] = scope.launch { runPipeline(runId) }
    }

    override fun pause(runId: String) {
        jobs.remove(runId)?.cancel()
        scope.launch {
            setStatus(runId, RunStatus.PAUSED, "Paused — will resume from last completed chunk")
        }
    }

    override fun cancel(runId: String) {
        jobs.remove(runId)?.cancel()
        scope.launch {
            setStatus(runId, RunStatus.CANCELLED, "Cancelled by user")
        }
    }

    override fun resume(runId: String) {
        start(runId)
    }

    private suspend fun setStatus(runId: String, status: RunStatus, stage: String) {
        val run = database.runDao().get(runId) ?: return
        database.runDao().update(
            run.copy(status = status.name, stage = stage, updatedAt = System.currentTimeMillis())
        )
    }

    private suspend fun bumpProgress(runId: String, done: Int, total: Int, stage: String) {
        val run = database.runDao().get(runId) ?: return
        database.runDao().update(
            run.copy(
                doneChunks = done,
                totalChunks = maxOf(run.totalChunks, total),
                stage = stage,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun runPipeline(runId: String) {
        try {
            val run0 = database.runDao().get(runId) ?: return
            val project = database.projectDao().get(run0.projectId) ?: return

            // ---- Stage 1: audio extraction (skipped when resuming) ----
            val wav = paths.extractedWav(project.id)
            if (!wav.exists() || wav.length() < WavFile.BYTES_PER_SAMPLE) {
                setStatus(runId, RunStatus.EXTRACTING, "Extracting 16 kHz mono audio")
                var lastReported = -1
                val result = audioExtractor.extractToWavMono16k(
                    Uri.parse(project.mediaUri),
                    wav
                ) { fraction ->
                    val pct = (fraction * 100).toInt()
                    if (pct / 10 != lastReported / 10) {
                        lastReported = pct
                        // Fire-and-forget stage update; DB writes are cheap and idempotent.
                        scope.launch {
                            setStatus(runId, RunStatus.EXTRACTING, "Extracting audio — $pct%")
                        }
                    }
                }
                database.projectDao().updateDuration(project.id, result.durationMs / 1000.0)
            }

            val run = database.runDao().get(runId) ?: return
            val totalMs = (database.projectDao().get(project.id)?.durationSec ?: 0.0).toLong() * 1000
            val effectiveTotalMs = if (totalMs > 0) totalMs else WavFile.parse(wav).dataBytes / 2 * 1000 / WavFile.SAMPLE_RATE

            // ---- Stage 2: chunk planning (idempotent on resume) ----
            val existing = database.chunkDao().forRun(runId)
            val ranges = ChunkPlanner.plan(effectiveTotalMs, run.chunkSeconds)
            if (existing.isEmpty()) {
                database.chunkDao().insertAll(
                    ranges.map {
                        ChunkEntity(
                            runId = runId,
                            index = it.index,
                            startMs = it.startMs,
                            endMs = it.endMs,
                            status = ChunkStatus.PENDING.name,
                            errorInfo = null,
                            segmentCount = 0
                        )
                    }
                )
            }

            // ---- Stage 3: model + engine ----
            val spec = ModelCatalog.byId(run.modelId) ?: ModelCatalog.BASE_Q5_1
            val modelFile = modelRepository.fileFor(spec)
            require(modelRepository.isInstalled(spec)) {
                "Model ${spec.displayName} is not installed — download it in the Models tab first"
            }

            setStatus(runId, RunStatus.TRANSCRIBING, "Loading ${spec.displayName}")
            val engine = WhisperEngine()
            try {
                engine.open(modelFile.absolutePath)
                val config = settings.current()
                val threads = config.transcribeThreads
                val translate = run.mode == "translate"

                // ---- Stage 4: chunked inference with per-chunk checkpoints ----
                val chunks = database.chunkDao().forRun(runId)
                var done = chunks.count { it.status == ChunkStatus.DONE.name }
                val total = chunks.size
                bumpProgress(runId, done, total, "Whisper $done/$total chunks")

                for (chunk in chunks) {
                    currentCoroutineContext().ensureActive() // checkpoint between chunks
                    if (chunk.status == ChunkStatus.DONE.name) continue

                    database.chunkDao().updateStatus(
                        runId, chunk.index, ChunkStatus.RUNNING.name, null, 0
                    )
                    val chunkWav = paths.chunkWav(project.id, chunk.index)
                    try {
                        val sliceMs = WavFile.sliceMono16k(wav, chunkWav, chunk.startMs, chunk.endMs)
                        require(sliceMs > 0) { "Empty chunk ${chunk.index}" }

                        val result = engine.transcribe(
                            chunkWav.absolutePath,
                            threads,
                            translate,
                            run.language
                        )
                        val segments = result.segments.mapIndexed { i, s ->
                            SegmentEntity(
                                runId = runId,
                                seq = chunk.index * 1_000_000 + i,
                                startMs = chunk.startMs + s.startMs,
                                endMs = chunk.startMs + s.endMs,
                                englishText = if (translate) s.text.trim() else "",
                                originalText = s.text.trim(),
                                sourceChunk = chunk.index
                            )
                        }
                        if (segments.isNotEmpty()) {
                            database.segmentDao().insertAll(segments)
                        }
                        database.chunkDao().updateStatus(
                            runId, chunk.index, ChunkStatus.DONE.name, null, segments.size
                        )
                        done++
                        bumpProgress(runId, done, total, "Whisper $done/$total chunks")
                    } catch (ce: CancellationException) {
                        database.chunkDao().updateStatus(
                            runId, chunk.index, ChunkStatus.PENDING.name, null, 0
                        )
                        throw ce
                    } catch (t: Throwable) {
                        database.chunkDao().updateStatus(
                            runId, chunk.index, ChunkStatus.FAILED.name, t.message ?: t.javaClass.simpleName, 0
                        )
                    } finally {
                        // Chunk WAVs are re-sliced on resume, so they never pile up.
                        chunkWav.delete()
                    }
                }

                // ---- Stage 5: rebuild + final status ----
                setStatus(runId, RunStatus.BUILDING, "Rebuilding subtitle order")
                renumberSegments(runId)
                val failed = database.chunkDao().forRun(runId).count {
                    it.status == ChunkStatus.FAILED.name
                }
                val hasSegments = database.segmentDao().countForRun(runId) > 0
                val finalStatus = when {
                    failed == 0 -> RunStatus.DONE
                    hasSegments -> RunStatus.PARTIAL
                    else -> RunStatus.FAILED
                }
                val stageText = if (failed == 0) {
                    "Complete — ${database.segmentDao().countForRun(runId)} captions"
                } else {
                    "$failed chunk(s) failed — resume to retry them"
                }
                setStatus(runId, finalStatus, stageText)
            } finally {
                engine.close()
            }
        } catch (ce: CancellationException) {
            // pause() or cancel(): only PAUSED if cancel() hasn't already recorded CANCELLED.
            val current = database.runDao().get(runId)
            if (current != null && current.status != RunStatus.CANCELLED.name &&
                current.status != RunStatus.FAILED.name
            ) {
                setStatus(runId, RunStatus.PAUSED, "Paused — will resume from last completed chunk")
            }
        } catch (t: Throwable) {
            setStatus(runId, RunStatus.FAILED, t.message ?: "Unexpected pipeline error")
        } finally {
            jobs.remove(runId)
        }
    }

    private suspend fun renumberSegments(runId: String) {
        val segments = database.segmentDao().forRun(runId)
        segments
            .sortedBy { it.startMs }
            .forEachIndexed { i, s -> database.segmentDao().updateSeq(s.id, i) }
    }
}
