package com.momosubtitle.feature.models

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.momosubtitle.core.ui.MonoSmall
import com.momosubtitle.core.common.Graph
import com.momosubtitle.core.common.SettingsRepository
import com.momosubtitle.core.common.TimeFormat
import com.momosubtitle.core.models.DownloadProgress
import com.momosubtitle.core.models.ModelCatalog
import com.momosubtitle.core.models.ModelRepository
import com.momosubtitle.core.models.ModelSpec
import kotlinx.coroutines.launch

/**
 * Owns Models-tab state. It never runs downloads itself: download/cancel are
 * forwarded to the ModelDownloadService via AppServicesBridge (background-safe,
 * UI-independent), and this ViewModel only observes ModelRepository.downloads —
 * the single shared progress stream owned by the repository instance in
 * [ModelRepository.shared].
 */
class ModelsViewModel(
    private val repo: ModelRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    val specs: List<ModelSpec> = ModelCatalog.all
    val downloads = repo.downloads

    var activeId by mutableStateOf("base-q5_1")
        private set
    var downloading by mutableStateOf<String?>(null)
        private set
    var refreshTick by mutableStateOf(0)
        private set

    private val autoActivated = mutableSetOf<String>()

    init {
        viewModelScope.launch { activeId = settings.current().activeModelId }
        viewModelScope.launch {
            repo.downloads.collect { map ->
                map.forEach { (id, progress) ->
                    when {
                        progress.done && id !in autoActivated -> {
                            // A finished download becomes the active model —
                            // mirrors the old inline flow, now background-safe.
                            autoActivated += id
                            settings.setActiveModel(id)
                            activeId = id
                            refreshTick++
                            if (downloading == id) downloading = null
                        }
                        progress.error != null && downloading == id -> downloading = null
                    }
                }
            }
        }
    }

    fun isInstalled(spec: ModelSpec): Boolean = repo.isInstalled(spec)

    fun setActive(id: String) {
        viewModelScope.launch {
            settings.setActiveModel(id)
            activeId = id
            refreshTick++
        }
    }

    /** Hand the download to the foreground service; progress arrives via [downloads]. */
    fun download(context: Context, spec: ModelSpec) {
        downloading = spec.id
        Graph.modelDownload.start(context, spec.id)
    }

    fun cancelDownload(context: Context) {
        downloading?.let { Graph.modelDownload.cancel(context, it) }
        downloading = null
    }

    fun delete(spec: ModelSpec) {
        repo.delete(spec)
        autoActivated.remove(spec.id)
        refreshTick++
    }
}

@Composable
fun ModelsScreen() {
    val context = LocalContext.current
    val vm: ModelsViewModel = viewModel {
        ModelsViewModel(ModelRepository.shared(context, Graph.settings), Graph.settings)
    }
    val downloads by vm.downloads.collectAsState()

    // Notification permission flow (API 33+): a plain-language rationale first,
    // then the system dialog. "Not now" still starts the download — progress
    // just stays inside the app instead of the notification bar.
    var rationaleFor by remember { mutableStateOf<ModelSpec?>(null) }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        val spec = rationaleFor
        rationaleFor = null
        if (spec != null) vm.download(context, spec)
    }

    fun requestDownload(spec: ModelSpec) {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) rationaleFor = spec else vm.download(context, spec)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp)
    ) {
        item {
            Text("Models", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Multilingual Whisper models. Weights download once with SHA-256 verification " +
                    "and run fully offline afterwards.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }
        items(vm.specs, key = { it.id }) { spec ->
            var confirmDelete by remember { mutableStateOf(false) }
            ModelCard(
                spec = spec,
                installed = vm.isInstalled(spec),
                active = vm.activeId == spec.id,
                progress = downloads[spec.id],
                downloading = vm.downloading == spec.id,
                onDownload = { requestDownload(spec) },
                onCancel = { vm.cancelDownload(context) },
                onDelete = { confirmDelete = true },
                onSetActive = { vm.setActive(spec.id) }
            )
            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text("Delete ${spec.displayName}?") },
                    text = { Text("You can re-download it later; progress downloads resume instead of restarting.") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmDelete = false
                            vm.delete(spec)
                        }) { Text("Delete") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    rationaleFor?.let { spec ->
        AlertDialog(
            onDismissRequest = { rationaleFor = null },
            title = { Text("Show download progress?") },
            text = {
                Text(
                    "Downloads keep running in the background even if you switch apps. " +
                        "Allow notifications so Momo Subtitle can show progress and completion " +
                        "in your notification bar."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        rationaleFor = null
                        vm.download(context, spec)
                    }
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = {
                    rationaleFor = null
                    vm.download(context, spec)
                }) { Text("Not now") }
            }
        )
    }
}

@Composable
private fun ModelCard(
    spec: ModelSpec,
    installed: Boolean,
    active: Boolean,
    progress: DownloadProgress?,
    downloading: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onSetActive: () -> Unit
) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        spec.displayName.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        spec.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "${TimeFormat.humanBytes(spec.sizeBytes)} · ggml Q5_1 · multilingual",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    when {
                        active -> "ACTIVE"
                        downloading -> "DOWNLOADING"
                        else -> spec.tier.uppercase()
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        active || downloading -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(spec.description, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "SHA-256 ${spec.sha256.take(16)}…",
                style = MonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val failed = progress?.error
            if (failed != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    failed,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (downloading && progress != null && !progress.done) {
                Spacer(Modifier.height(12.dp))
                val fraction = if (progress.totalBytes > 0) {
                    (progress.downloadedBytes.toFloat() / progress.totalBytes).coerceIn(0f, 1f)
                } else 0f
                LinearProgressIndicator(
                    progress = { fraction },
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${(fraction * 100).toInt()}% · " +
                            "${TimeFormat.humanBytes(progress.downloadedBytes)} / " +
                            TimeFormat.humanBytes(progress.totalBytes),
                        style = MonoSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        etaText(progress),
                        style = MonoSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Runs in the background — safe to leave this screen.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))
            Row {
                when {
                    downloading -> {
                        OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    }
                    installed -> {
                        if (active) {
                            OutlinedButton(onClick = onSetActive, enabled = false) { Text("Active model") }
                        } else {
                            Button(onClick = onSetActive) { Text("Set active") }
                        }
                        TextButton(onClick = onDelete) { Text("Delete") }
                    }
                    else -> {
                        Button(onClick = onDownload) {
                            Text(
                                if (failed != null) "Retry · ${TimeFormat.humanBytes(spec.sizeBytes)}"
                                else "Download · ${TimeFormat.humanBytes(spec.sizeBytes)}"
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun etaText(progress: DownloadProgress): String {
    val remaining = progress.totalBytes - progress.downloadedBytes
    if (progress.speedBps <= 0 || remaining <= 0) return ""
    val seconds = remaining / progress.speedBps
    val label = when {
        seconds < 5 -> "seconds"
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
    return "~$label left"
}
