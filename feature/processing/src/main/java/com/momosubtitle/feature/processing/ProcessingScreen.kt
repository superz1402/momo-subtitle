package com.momosubtitle.feature.processing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.momosubtitle.core.common.Graph
import com.momosubtitle.core.common.TimeFormat
import com.momosubtitle.core.common.job.ChunkStatus
import com.momosubtitle.core.common.job.RunStatus
import com.momosubtitle.core.subtitles.CueBuilder
import com.momosubtitle.core.subtitles.SubtitleSegment
import com.momosubtitle.core.ui.Aqua
import com.momosubtitle.core.ui.ErrRed
import com.momosubtitle.core.ui.InfoBlue
import com.momosubtitle.core.ui.MonoBody
import com.momosubtitle.core.ui.MonoSmall
import com.momosubtitle.core.ui.OkGreen
import com.momosubtitle.core.ui.WarnAmber
import com.momosubtitle.feature.export.SubtitleFileExporter
import com.momosubtitle.feature.export.SubtitleFormat
import kotlinx.coroutines.launch

/**
 * Real processing progress (blueprint section 7): stage, per-chunk counts,
 * pause/resume/cancel, failed chunk reasons, and live caption preview.
 */
@Composable
fun ProcessingScreen(runId: String, onBack: () -> Unit) {
    val db = Graph.database
    val run by db.runDao().observe(runId).collectAsState(initial = null)
    val projects by db.projectDao().observeAll().collectAsState(initial = emptyList())
    val chunks by db.chunkDao().observeForRun(runId).collectAsState(initial = emptyList())
    val segments by db.segmentDao().observeForRun(runId).collectAsState(initial = emptyList())

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = remember { SubtitleFileExporter(context) }
    val project = projects.firstOrNull { it.id == run?.projectId }

    fun cues() = CueBuilder.build(
        segments.map {
            SubtitleSegment(it.startMs, it.endMs, it.englishText.ifBlank { it.originalText.orEmpty() })
        }
    )

    val srtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri -> uri?.let { u -> scope.launch { exporter.write(u, SubtitleFormat.SRT, cues()) } } }
    val vttLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/vtt")
    ) { uri -> uri?.let { u -> scope.launch { exporter.write(u, SubtitleFormat.VTT, cues()) } } }
    val txtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { u -> scope.launch { exporter.write(u, SubtitleFormat.TXT, cues()) } } }

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Column {
                Text(
                    project?.name ?: "Processing",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    run?.stage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val r = run
        if (r == null) {
            Text("Run not found.", Modifier.padding(20.dp))
            return
        }
        val status = RunStatus.safe(r.status)

        LinearProgressIndicator(
            progress = {
                if (r.totalChunks > 0) r.doneChunks.toFloat() / r.totalChunks else 0f
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(4.dp)
        )

        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(status, r.doneChunks, r.totalChunks)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${r.doneChunks}/${r.totalChunks} chunks · ${r.modelId} · ${r.mode}",
                    style = MonoBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))
            when (status) {
                RunStatus.QUEUED, RunStatus.EXTRACTING, RunStatus.TRANSCRIBING, RunStatus.BUILDING -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { Graph.jobController.pause(runId) }) { Text("Pause") }
                        TextButton(onClick = { Graph.jobController.cancel(runId) }) { Text("Cancel") }
                    }
                }
                RunStatus.PAUSED, RunStatus.PARTIAL, RunStatus.FAILED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { Graph.jobController.resume(runId) }) {
                            Text(if (status == RunStatus.PAUSED) "Resume" else "Retry failed chunks")
                        }
                        TextButton(onClick = { Graph.jobController.cancel(runId) }) { Text("Cancel") }
                    }
                }
                else -> Unit
            }

            if (status == RunStatus.DONE || status == RunStatus.PARTIAL) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Export subtitles",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { srtLauncher.launch("${project?.name ?: "subtitles"}-en.srt") }) {
                        Text("SRT")
                    }
                    OutlinedButton(onClick = { vttLauncher.launch("${project?.name ?: "subtitles"}-en.vtt") }) {
                        Text("VTT")
                    }
                    OutlinedButton(onClick = { txtLauncher.launch("${project?.name ?: "subtitles"}-en.txt") }) {
                        Text("TXT")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Subtitles export independently of the source video.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (chunks.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                val doneN = chunks.count { it.status == ChunkStatus.DONE.name }
                val failedN = chunks.count { it.status == ChunkStatus.FAILED.name }
                val pendingN = chunks.size - doneN - failedN
                Text(
                    "$doneN done · $failedN failed · $pendingN pending",
                    style = MonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                chunks.filter { it.status == ChunkStatus.FAILED.name }.take(3).forEach { c ->
                    Text(
                        "Chunk ${c.index}: ${c.errorInfo ?: "error"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Captions (${segments.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        LazyColumn(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(segments, key = { it.id }) { seg ->
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            TimeFormat.stamp(seg.startMs),
                            style = MonoSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("→", style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            TimeFormat.stamp(seg.endMs),
                            style = MonoSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        seg.englishText.ifBlank { seg.originalText ?: "" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (seg.englishText.isNotBlank() && !seg.originalText.isNullOrBlank()) {
                        Text(
                            seg.originalText!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

/** Status is always paired with a label — never color-only (docs/DESIGN_SYSTEM.md). */
@Composable
private fun StatusChip(status: RunStatus?, doneChunks: Int, totalChunks: Int) {
    val (label, color) = when (status) {
        RunStatus.DONE -> "Complete" to OkGreen
        RunStatus.PARTIAL -> "Partial" to WarnAmber
        RunStatus.FAILED -> "Failed" to ErrRed
        RunStatus.CANCELLED -> "Cancelled" to InfoBlue
        RunStatus.PAUSED -> "Paused" to WarnAmber
        else ->
            if (totalChunks > 0) "$doneChunks/$totalChunks" to Aqua
            else "Queued" to InfoBlue
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.16f)) {
        Text(
            label,
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
