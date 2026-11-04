package com.momosubtitle.feature.imports

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.momosubtitle.core.common.Graph
import com.momosubtitle.core.common.TimeFormat
import com.momosubtitle.core.common.job.ImportRequest
import com.momosubtitle.core.models.ModelRepository
import com.momosubtitle.core.storage.SafUtils
import com.momosubtitle.core.translation.TranslationMode
import kotlinx.coroutines.launch

private val LANGUAGES = listOf(
    "auto" to "Auto detect",
    "ko" to "Korean",
    "zh" to "Mandarin",
    "th" to "Thai",
    "ja" to "Japanese",
    "en" to "English"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(
    onStarted: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by Graph.settings.settings.collectAsState(initial = null)

    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var mediaName by remember { mutableStateOf<String?>(null) }
    var mediaSize by remember { mutableStateOf(-1L) }
    var mode by remember { mutableStateOf("translate") }
    var language by remember { mutableStateOf("auto") }
    var chunkSeconds by remember { mutableStateOf(30) }
    var modelId by remember(settings?.activeModelId) {
        mutableStateOf(settings?.activeModelId ?: "base-q5_1")
    }
    var error by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }

    val modelRepo = remember { ModelRepository(context, Graph.settings) }
    val installed = modelRepo.installedModels()

    LaunchedEffect(Unit) { mode = settings?.defaultMode ?: "translate" }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            SafUtils.takePersistableRead(context, uri)
            val meta = SafUtils.queryMeta(context, uri)
            mediaUri = uri
            mediaName = meta?.first
            mediaSize = meta?.second ?: -1
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Import media", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = { picker.launch(arrayOf("audio/*", "video/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Choose audio or video")
        }
        Spacer(Modifier.height(12.dp))

        if (mediaUri != null) {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(mediaName ?: "media", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        TimeFormat.humanBytes(mediaSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SectionHeader("Output mode")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TranslationMode.entries.forEach { tm ->
                FilterChip(
                    selected = mode == tm.id,
                    enabled = tm.available,
                    onClick = { mode = tm.id },
                    label = { Text(if (tm.available) tm.label else "${tm.label} — later") }
                )
            }
        }
        Text(
            TranslationMode.byId(mode).note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionHeader("Spoken language")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LANGUAGES.forEach { (code, label) ->
                FilterChip(
                    selected = language == code,
                    onClick = { language = code },
                    label = { Text(label) }
                )
            }
        }
        Text(
            "Mixed-language interviews should stay on Auto detect — language is re-detected per chunk.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionHeader("Chunk length")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 45, 60).forEach { secs ->
                FilterChip(
                    selected = chunkSeconds == secs,
                    onClick = { chunkSeconds = secs },
                    label = { Text("$secs s") }
                )
            }
        }

        SectionHeader("Model")
        if (installed.isEmpty()) {
            Text(
                "No model installed yet — open the Models tab and download the Base multilingual Q5_1.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                installed.forEach { spec ->
                    FilterChip(
                        selected = modelId == spec.id,
                        onClick = { modelId = spec.id },
                        label = { Text(spec.displayName) }
                    )
                }
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Card {
                Text(
                    it,
                    Modifier.fillMaxWidth().padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            enabled = mediaUri != null && installed.isNotEmpty() && !starting,
            onClick = {
                val uri = mediaUri ?: return@Button
                starting = true
                error = null
                scope.launch {
                    try {
                        val runId = Graph.jobController.createAndStartRun(
                            ImportRequest(
                                mediaUri = uri.toString(),
                                displayName = mediaName ?: "media",
                                modelId = modelId,
                                mode = mode,
                                language = language,
                                chunkSeconds = chunkSeconds
                            )
                        )
                        starting = false
                        onStarted(runId)
                    } catch (t: Throwable) {
                        starting = false
                        error = t.message ?: "Failed to start"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (starting) "Starting…" else "Start transcription")
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
}
