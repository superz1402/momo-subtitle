package com.momosubtitle.feature.models

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.momosubtitle.core.ui.MonoSmall
import com.momosubtitle.core.common.Graph
import com.momosubtitle.core.common.SettingsRepository
import com.momosubtitle.core.common.TimeFormat
import com.momosubtitle.core.models.ModelCatalog
import com.momosubtitle.core.models.ModelRepository
import com.momosubtitle.core.models.ModelSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
    var error by mutableStateOf<String?>(null)
        private set
    var refreshTick by mutableStateOf(0)
        private set

    private var currentJob: Job? = null

    init {
        viewModelScope.launch { activeId = settings.current().activeModelId }
    }

    fun isInstalled(spec: ModelSpec): Boolean = repo.isInstalled(spec)

    fun setActive(id: String) {
        viewModelScope.launch {
            settings.setActiveModel(id)
            activeId = id
            refreshTick++
        }
    }

    fun download(spec: ModelSpec) {
        if (currentJob?.isActive == true) return
        error = null
        downloading = spec.id
        currentJob = viewModelScope.launch {
            try {
                repo.download(spec)
                setActive(spec.id)
            } catch (ce: CancellationException) {
                // user cancelled; .part file stays for resume
            } catch (t: Throwable) {
                error = t.message ?: "Download failed"
            } finally {
                downloading = null
                refreshTick++
            }
        }
    }

    fun cancelDownload() {
        currentJob?.cancel()
        downloading = null
    }

    fun delete(spec: ModelSpec) {
        repo.delete(spec)
        refreshTick++
    }
}

@Composable
fun ModelsScreen() {
    val context = LocalContext.current
    val vm: ModelsViewModel = viewModel {
        ModelsViewModel(ModelRepository(context, Graph.settings), Graph.settings)
    }
    val downloads by vm.downloads.collectAsState()

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
                onDownload = { vm.download(spec) },
                onCancel = { vm.cancelDownload() },
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
        item {
            vm.error?.let { err ->
                Card {
                    Text(
                        err,
                        Modifier.fillMaxWidth().padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    spec: ModelSpec,
    installed: Boolean,
    active: Boolean,
    progress: com.momosubtitle.core.models.DownloadProgress?,
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
                    if (active) "ACTIVE" else spec.tier.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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

            if (downloading && progress != null && !progress.done) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = {
                        if (progress.totalBytes > 0) {
                            progress.downloadedBytes.toFloat() / progress.totalBytes
                        } else 0f
                    },
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${TimeFormat.humanBytes(progress.downloadedBytes)} / ${TimeFormat.humanBytes(progress.totalBytes)}",
                    style = MonoSmall,
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
                            Text("Download · ${TimeFormat.humanBytes(spec.sizeBytes)}")
                        }
                    }
                }
            }
        }
    }
}
