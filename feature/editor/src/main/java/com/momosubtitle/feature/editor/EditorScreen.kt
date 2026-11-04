package com.momosubtitle.feature.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.momosubtitle.core.common.Graph
import com.momosubtitle.core.common.TimeFormat
import com.momosubtitle.core.ui.MonoBody
import com.momosubtitle.core.ui.MonoSmall

private val PLANNED_FEATURES = listOf(
    "Video preview (Media3)" to "Phase 3",
    "Edit caption text" to "Phase 3",
    "Change start/end timestamps" to "Phase 3",
    "Add / delete captions" to "Phase 3",
    "Merge / split captions" to "Phase 3",
    "Find and replace" to "Phase 3",
    "Global timing shift" to "Phase 3",
    "Undo / redo" to "Phase 3",
    "Original-language comparison" to "Phase 3",
    "Waveform timeline" to "Phase 4",
    "Word-level alignment" to "Phase 4"
)

/**
 * Milestone 0.1 ships the studio as a read-only caption preview; the full
 * editing surface is the Phase 3 deliverable (docs/ARCHITECTURE.md).
 */
@Composable
fun EditorScreen(runId: String, onBack: () -> Unit) {
    val db = Graph.database
    val runs by db.runDao().observeRecent(50).collectAsState(initial = emptyList())
    val segments by db.segmentDao().observeForRun(runId).collectAsState(initial = emptyList())
    val run = runs.firstOrNull { it.id == runId }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text("Subtitle Studio", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Text(
                "PHASE 3",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "The editing studio arrives in Phase 3. What you see below is a read-only " +
                "preview of this run's captions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))
        Text("Planned capabilities", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                PLANNED_FEATURES.forEach { (feature, phase) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(feature, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(phase, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Captions in this run: ${segments.size}",
            style = MonoBody,
            color = MaterialTheme.colorScheme.primary
        )
        run?.let {
            Text(
                "status: ${it.status} · mode: ${it.mode} · language: ${it.language}",
                style = MonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                segments.take(15).forEach { seg ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(
                            "${TimeFormat.stamp(seg.startMs)} → ${TimeFormat.stamp(seg.endMs)}",
                            style = MonoSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(seg.englishText.ifBlank { seg.originalText ?: "" }, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (segments.size > 15) {
                    Spacer(Modifier.height(4.dp))
                    Text("… ${segments.size - 15} more", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
