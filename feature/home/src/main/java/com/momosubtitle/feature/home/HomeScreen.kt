package com.momosubtitle.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.momosubtitle.core.ui.Aqua
import com.momosubtitle.core.ui.ErrRed
import com.momosubtitle.core.ui.InfoBlue
import com.momosubtitle.core.ui.OkGreen
import com.momosubtitle.core.ui.WarnAmber
import com.momosubtitle.core.common.Graph
import com.momosubtitle.core.common.TimeFormat
import com.momosubtitle.core.common.job.RunStatus

@Composable
fun HomeScreen(
    onStartProject: () -> Unit,
    onOpenRun: (String) -> Unit
) {
    val db = Graph.database
    val runs by db.runDao().observeRecent(20).collectAsState(initial = emptyList())
    val projects by db.projectDao().observeAll().collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    "Momo Subtitle",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "YOUR LOCAL SUBTITLE WORKSPACE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        item {
            Button(onClick = onStartProject, modifier = Modifier.fillMaxWidth()) {
                Text("Start a project")
            }
        }
        item {
            Text(
                "Recent projects",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (runs.isEmpty()) {
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Nothing here yet", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Import a full-length interview and turn it into synchronized " +
                                "English subtitles — completely offline after model installation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        items(runs, key = { it.id }) { run ->
            val project = projects.firstOrNull { it.id == run.projectId }
            RunCard(run, project) { onOpenRun(run.id) }
        }
    }
}

@Composable
private fun RunCard(
    run: com.momosubtitle.core.database.TranscriptionRunEntity,
    project: com.momosubtitle.core.database.ProjectEntity?,
    onClick: () -> Unit
) {
    Card {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        project?.name ?: "Untitled project",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${TimeFormat.duration(project?.durationSec ?: 0.0)} · ${run.modelId} · ${run.stage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusChip(RunStatus.safe(run.status), run.doneChunks, run.totalChunks)
            }
        }
    }
}

/** Status is always paired with a label — never color-only (docs/DESIGN_SYSTEM.md). */
@Composable
fun StatusChip(status: RunStatus?, doneChunks: Int, totalChunks: Int) {
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
