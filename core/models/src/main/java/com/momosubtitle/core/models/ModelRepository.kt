package com.momosubtitle.core.models

import android.content.Context
import com.momosubtitle.core.common.SettingsRepository
import com.momosubtitle.core.storage.MomoPaths
import kotlinx.coroutines.Dispatchers
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

data class DownloadProgress(
    val specId: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val done: Boolean
)

/**
 * Model manager: download with resume (.part + HTTP Range), then verify size
 * and SHA-256 in a second pass before the file becomes visible. Interrupted
 * downloads resume from where they stopped; a bad digest never reaches the
 * engine (blueprint requirement: SHA-256 verification).
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
    }

    fun deleteById(id: String) {
        ModelCatalog.byId(id)?.let { delete(it) }
    }

    fun progressFor(specId: String): DownloadProgress? = _downloads.value[specId]

    suspend fun download(spec: ModelSpec) = withContext(Dispatchers.IO) {
        if (isInstalled(spec)) return@withContext
        val part = partFile(spec)
        part.parentFile?.mkdirs()

        var offset = if (part.exists()) part.length() else 0L
        val conn = (URL(spec.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }
        try {
            val code = conn.responseCode
            val input: InputStream = when (code) {
                HttpURLConnection.HTTP_PARTIAL -> conn.inputStream
                HttpURLConnection.HTTP_OK -> {
                    offset = 0
                    conn.inputStream
                }
                else -> throw IOException("Model download failed: HTTP $code")
            }

            input.use { stream ->
                java.io.RandomAccessFile(part, "rw").use { raf ->
                    if (code == HttpURLConnection.HTTP_OK) raf.setLength(0)
                    raf.seek(offset)
                    val buffer = ByteArray(256 * 1024)
                    var written = offset
                    while (true) {
                        ensureActive() // cancellation keeps the .part file for resume
                        val n = stream.read(buffer)
                        if (n < 0) break
                        raf.write(buffer, 0, n)
                        written += n
                        _downloads.value = _downloads.value +
                            (spec.id to DownloadProgress(spec.id, written, spec.sizeBytes, false))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        require(part.length() == spec.sizeBytes) {
            "Incomplete download: ${part.length()} of ${spec.sizeBytes} bytes"
        }
        verifySha256(part, spec)

        val target = fileFor(spec)
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        _downloads.value = _downloads.value +
            (spec.id to DownloadProgress(spec.id, spec.sizeBytes, spec.sizeBytes, true))
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
            file.delete()
            throw IOException("SHA-256 mismatch for ${spec.fileName} — download deleted, please retry")
        }
    }
}
