package com.momosubtitle.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.momosubtitle.core.common.Graph
import com.momosubtitle.core.common.MomoSettings
import com.momosubtitle.core.common.SettingsRepository
import com.momosubtitle.core.common.TimeFormat
import com.momosubtitle.core.models.ModelRepository
import com.momosubtitle.core.speech.WhisperJni
import com.momosubtitle.core.ui.MonoBody
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    appContext: Context
) : ViewModel() {

    var state by mutableStateOf(MomoSettings(2, 30, "auto", "translate", "base-q5_1"))
        private set
    var modelBytes by mutableStateOf(0L)
        private set
    var systemInfo by mutableStateOf("")
        private set

    init {
        systemInfo = runCatching { WhisperJni.systemInfo() }.getOrDefault("native lib unavailable")
        val repo = ModelRepository(appContext, settings)
        viewModelScope.launch {
            state = settings.current()
            modelBytes = repo.totalModelBytes()
        }
    }

    fun setThreads(v: Int) = viewModelScope.launch {
        settings.setThreads(v)
        state = state.copy(transcribeThreads = v)
    }

    fun setChunkSeconds(v: Int) = viewModelScope.launch {
        settings.setChunkSeconds(v)
        state = state.copy(chunkSeconds = v)
    }

    fun setLanguage(v: String) = viewModelScope.launch {
        settings.setDefaultLanguage(v)
        state = state.copy(defaultLanguage = v)
    }

    fun setMode(v: String) = viewModelScope.launch {
        settings.setDefaultMode(v)
        state = state.copy(defaultMode = v)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(Graph.settings, context.applicationContext) }
    val s = vm.state

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(16.dp))
        SettingsCard("Performance") {
            Text(
                "Whisper CPU threads: ${s.transcribeThreads}",
                style = MonoBody
            )
            Slider(
                value = s.transcribeThreads.toFloat(),
                onValueChange = { vm.setThreads(it.toInt()) },
                valueRange = 1f..8f,
                steps = 6
            )
            Spacer(Modifier.height(8.dp))
            Text("Chunk length: ${s.chunkSeconds} s", style = MonoBody)
            Spacer(Modifier.height(8.dp))
            FlowRow {
                listOf(30, 45, 60).forEach { secs ->
                    FilterChip(
                        selected = s.chunkSeconds == secs,
                        onClick = { vm.setChunkSeconds(secs) },
                        label = { Text("$secs s") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            Text(
                "Benchmark 30/45/60 s on your device — the best balance differs per phone (blueprint §4).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsCard("Defaults") {
            Text("Spoken language", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow {
                listOf("auto" to "Auto", "ko" to "Korean", "zh" to "Mandarin", "th" to "Thai", "ja" to "Japanese", "en" to "English").forEach { (code, label) ->
                    FilterChip(
                        selected = s.defaultLanguage == code,
                        onClick = { vm.setLanguage(code) },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Output mode", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow {
                listOf("translate" to "English (Whisper translate)", "transcribe" to "Original transcript").forEach { (id, label) ->
                    FilterChip(
                        selected = s.defaultMode == id,
                        onClick = { vm.setMode(id) },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }

        SettingsCard("Storage") {
            Text("Model storage: ${TimeFormat.humanBytes(vm.modelBytes)}", style = MonoBody)
            Spacer(Modifier.height(4.dp))
            Text(
                "Working files (extracted audio, chunks) live in app-private storage. " +
                    "You choose the export destination every time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsCard("About") {
            Text("Momo Subtitle · 0.1.1", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "All speech recognition and translation run locally. No account, no API keys, " +
                    "no subscription. Internet is used only to download model weights.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Engine: whisper.cpp (MIT) · Models: OpenAI Whisper-derived weights (MIT) · " +
                    "See LICENSE for third-party notices.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
