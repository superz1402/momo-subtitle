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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
import com.momosubtitle.core.ui.EmptyStateView
import com.momosubtitle.core.ui.ErrRed
import com.momosubtitle.core.ui.InfoBlue
import com.momosubtitle.core.ui.OkGreen
import com.momosubtitle.core.ui.Radius
import com.momosubtitle.core.ui.Spacing
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

    val totalHours = (projects.sumOf { it.durationSec } / 3600.0)
        .let { if (it >= 1.0) "%.1f h".format(it) else TimeFormat.duration(projects.sumOf { p -> p.durationSec }) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Momo Subtitle",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "Workspace",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile("${projects.size}", "projects", Modifier.weight(1f))
                StatTile(totalHours, "of media", Modifier.weight(1f))
                StatTile("${runs.count { it.status == RunStatus.DONE.name }}", "completed", Modifier.weight(1f))
            }
        }
        item {
            Button(
                onClick = onStartProject,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("New subtitle project")
            }
        }
        item {
            Text(
                "Recent",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (runs.isEmpty()) {
            item {
                EmptyStateView(
                    icon = Icons.Filled.PlayArrow,
                    title = "Nothing here yet",
                    subtitle = "Import a full-length interview and turn it into synchronized " +
                        "English subtitles — completely offline after model installation.",
                    actionLabel = "Import media",
                    onAction = onStartProject
                )
            }
        }
        items(runs, key = { it.id }) { run ->
            val project = projects.firstOrNull { it.id == run.projectId }
            RunCard(run, project) { onOpenRun(run.id) }
        }
        item { Spacer(Modifier.height(Spacing.huge)) }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
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
                .padding(Spacing.lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        project?.name ?: "Untitled project",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        TimeFormat.duration(project?.durationSec ?: 0.0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                StatusChip(RunStatus.safe(run.status), run.doneChunks, run.totalChunks)
            }
            if (run.status != RunStatus.DONE.name) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    run.stage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
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
    Surface(shape = RoundedCornerShape(Radius.pill), color = color.copy(alpha = 0.16f)) {
        Text(
            label,
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
