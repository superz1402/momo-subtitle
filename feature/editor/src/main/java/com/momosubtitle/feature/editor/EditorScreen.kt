package com.momosubtitle.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.momosubtitle.core.common.Graph
import com.momosubtitle.core.common.TimeFormat
import com.momosubtitle.core.ui.EmptyStateView
import com.momosubtitle.core.ui.MomoBackHeader
import com.momosubtitle.core.ui.MonoSmall
import com.momosubtitle.core.ui.Spacing

/**
 * Subtitle Studio — read-only caption preview for this run. The interactive
 * editing surface is the Phase 3 deliverable (docs/ARCHITECTURE.md); the
 * placeholder roadmap card was removed for the Play-ready build.
 */
@Composable
fun EditorScreen(runId: String, onBack: () -> Unit) {
    val db = Graph.database
    val segments by db.segmentDao().observeForRun(runId).collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize()) {
        MomoBackHeader(title = "Subtitle Studio", onBack = onBack)

        if (segments.isEmpty()) {
            EmptyStateView(
                icon = Icons.Filled.Edit,
                title = "No captions yet",
                subtitle = "Run the transcription first — every generated caption will appear here.",
                actionLabel = null,
                onAction = null
            )
            return
        }

        Text(
            "${segments.size} captions",
            Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            items(segments, key = { it.id }) { seg ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
                        Text(
                            "${TimeFormat.stamp(seg.startMs)} → ${TimeFormat.stamp(seg.endMs)}",
                            style = MonoSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(Spacing.xs))
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
            }
            item { Spacer(Modifier.height(Spacing.huge)) }
        }
    }
}
