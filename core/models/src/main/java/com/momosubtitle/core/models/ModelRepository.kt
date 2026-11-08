package com.momosubtitle.core.models

import android.content.Context
import com.momosubtitle.core.common.SettingsRepository
import com.momosubtitle.core.storage.MomoPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.random.Random

data class DownloadProgress(
    val specId: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val done: Boolean,
    val error: String? = null,
    /** Bytes per second observed in the current attempt; 0 when idle/verifying. */
    val speedBps: Long = 0
)

/**
 * Model manager: download with resume (.part + HTTP Range), automatic retry
 * with exponential backoff for transient network failures ("software caused
 * connection abort", Wi-Fi drops, Doze network throttling), then verify size
 * and SHA-256 before the file becomes visible to the engine.
 *
 * One process-wide instance ([shared]) is shared between the Models screen and
 * [com.momosubtitle.app.service.ModelDownloadService] so both observe the same
 * progress stream. Heavy work never runs on the caller's thread: callers hand
 * the spec to the foreground service, which calls [download] on IO.
 */
class ModelRepository(
    private val context: Context,
    private val settings: SettingsRepository
) {

    private val paths = MomoPaths(context)
    private val modelsDir get() = paths.modelsDir

    private val _downloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadProgress>> = _downloads

    fun fileFor(spec: ModelSpec): File = File(modelsDir, spec.fileName)

    private fun partFile(spec: ModelSpec): File = File(modelsDir, spec.fileName + ".part")

    fun isInstalled(spec: ModelSpec): Boolean {
        val f = fileFor(spec)
        return f.exists() && f.length() == spec.sizeBytes
    }

    fun installedModels(): List<ModelSpec> = ModelCatalog.all.filter { isInstalled(it) }

    fun totalModelBytes(): Long =
        modelsDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    suspend fun setActiveModel(id: String) = settings.setActiveModel(id)

    suspend fun activeModelId(): String = settings.current().activeModelId

    /** Null when the active model is not installed — the UI must surface this. */
    suspend fun activeModelFile(): File? {
        val spec = ModelCatalog.byId(activeModelId()) ?: ModelCatalog.BASE_Q5_1
        return if (isInstalled(spec)) fileFor(spec) else null
    }

    fun fileForId(id: String): File? {
        val spec = ModelCatalog.byId(id) ?: return null
        return if (isInstalled(spec)) fileFor(spec) else null
    }

    fun delete(spec: ModelSpec) {
        fileFor(spec).delete()
        partFile(spec).delete()
        _downloads.value = _downloads.value - spec.id
    }

    fun deleteById(id: String) {
        ModelCatalog.byId(id)?.let { delete(it) }
    }

    fun progressFor(specId: String): DownloadProgress? = _downloads.value[specId]

    fun markFailed(specId: String, message: String) {
        val previous = _downloads.value[specId]
        _downloads.value = _downloads.value + (specId to DownloadProgress(
            specId = specId,
            downloadedBytes = previous?.downloadedBytes ?: 0L,
            totalBytes = ModelCatalog.byId(specId)?.sizeBytes ?: 0L,
            done = false,
            error = message
        ))
    }

    private fun clearError(specId: String) {
        val previous = _downloads.value[specId] ?: return
        if (previous.error != null) {
            _downloads.value = _downloads.value + (specId to previous.copy(error = null))
        }
    }

    private fun emitProgress(spec: ModelSpec, bytes: Long, speedBps: Long) {
        _downloads.value = _downloads.value + (spec.id to DownloadProgress(
            spec.id, bytes, spec.sizeBytes, done = false, speedBps = speedBps
        ))
    }

    /**
     * Download [spec] with up to [MAX_ATTEMPTS] attempts. Every attempt resumes
     * from the .part file via HTTP Range, so a connection abort loses at most
     * the bytes in flight — never the whole download. Throws only after the
     * final attempt fails; the last progress entry then carries [DownloadProgress.error].
     */
    suspend fun download(spec: ModelSpec) = withContext(Dispatchers.IO) {
        clearError(spec.id)
        if (isInstalled(spec)) {
            _downloads.value = _downloads.value +
                (spec.id to DownloadProgress(spec.id, spec.sizeBytes, spec.sizeBytes, done = true))
            return@withContext
        }
        val part = partFile(spec)
        part.parentFile?.mkdirs()

        var lastError: IOException? = null
        var verified = false
        for (attempt in 1..MAX_ATTEMPTS) {
            ensureActive()
            try {
                downloadOnce(spec, part)
                verifySha256(part, spec)
                verified = true
                break
            } catch (ce: CancellationException) {
                throw ce // user cancelled — keep the .part file for resume
            } catch (io: IOException) {
                lastError = io
                if (attempt == MAX_ATTEMPTS) break
                // Exponential backoff with jitter; delay() is cancellable.
                emitProgress(spec, part.length(), speedBps = 0)
                delay(BACKOFF_MS[attempt - 1] + Random.nextLong(1_000))
            }
        }

        if (!verified) {
            val message = friendly(lastError)
            markFailed(spec.id, message)
            throw IOException(message, lastError)
        }

        val target = fileFor(spec)
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        _downloads.value = _downloads.value +
            (spec.id to DownloadProgress(spec.id, spec.sizeBytes, spec.sizeBytes, done = true))
    }

    /** One HTTP attempt. Keeps [part] at whatever bytes landed (resume anchor). */
    private fun downloadOnce(spec: ModelSpec, part: File) {
        var offset = if (part.exists()) part.length() else 0L
        if (offset >= spec.sizeBytes) return // complete on disk; SHA check decides

        val conn = (URL(spec.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            // Identity encoding keeps byte offsets valid across Range resumes;
            // transparent gzip would corrupt the .part bookkeeping.
            setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }
        try {
            val code = conn.responseCode
            val input: InputStream = when (code) {
                HttpURLConnection.HTTP_PARTIAL -> conn.inputStream
                HttpURLConnection.HTTP_OK -> { offset = 0; conn.inputStream }
                HTTP_RANGE_NOT_SATISFIABLE -> {
                    // Server rejected the resume offset — discard and restart.
                    part.delete()
                    throw IOException("Resume rejected (HTTP 416) — restarting from zero")
                }
                else -> throw IOException("Server returned HTTP $code while downloading ${spec.fileName}")
            }

            input.use { stream ->
                java.io.RandomAccessFile(part, "rw").use { raf ->
                    if (code == HttpURLConnection.HTTP_OK) raf.setLength(0)
                    raf.seek(offset)
                    val buffer = ByteArray(256 * 1024)
                    var written = offset
                    var lastEmit = offset
                    val startedNanos = System.nanoTime()
                    while (true) {
                        ensureActive() // cancellation keeps the .part file for resume
                        val n = stream.read(buffer)
                        if (n < 0) break
                        raf.write(buffer, 0, n)
                        written += n
                        // Throttled emission keeps recomposition cheap for both
                        // the notification and the screen.
                        if (written - lastEmit >= EMIT_GRANULARITY_BYTES) {
                            lastEmit = written
                            emitProgress(spec, written, speedFor(offset, written, startedNanos))
                        }
                    }
                    emitProgress(spec, written, speedBps = 0)
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun speedFor(offset: Long, written: Long, startedNanos: Long): Long {
        val elapsed = (System.nanoTime() - startedNanos) / 1_000_000_000.0
        return if (elapsed > 0.5) ((written - offset) / elapsed).toLong() else 0L
    }

    private fun verifySha256(file: File, spec: ModelSpec) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(spec.sha256, ignoreCase = true)) {
            file.delete() // corrupt payload — next attempt starts clean
            throw IOException("SHA-256 mismatch for ${spec.fileName} — download deleted, retrying")
        }
    }

    private fun friendly(error: IOException?): String {
        val raw = error?.message ?: return "Download failed."
        val m = raw.lowercase()
        return when {
            "abort" in m || "reset" in m || "timeout" in m || "eof" in m || "broken pipe" in m ->
                "Network connection dropped after several retries. Tap Download to resume where it stopped."
            "unable to resolve" in m || "network" in m ->
                "No internet connection. Tap Download to try again."
            else -> raw
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 6
        private val BACKOFF_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 15_000, 30_000)
        private const val EMIT_GRANULARITY_BYTES = 512 * 1024L
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416

        @Volatile
        private var shared: ModelRepository? = null

        /**
         * Process-wide instance. The Models screen, the import flow and the
         * download foreground service all share this so progress (and the .part
         * bookkeeping) stay consistent no matter who started the download.
         */
        fun shared(context: Context, settings: SettingsRepository): ModelRepository =
            shared ?: synchronized(this) {
                shared ?: ModelRepository(context.applicationContext, settings).also { shared = it }
            }
    }
}
