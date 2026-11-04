package com.momosubtitle.core.subtitles

/**
 * Editorial defaults from the blueprint (section 4). These are configurable
 * product defaults, not universal subtitle standards.
 */
data class SubtitleRules(
    val maxCharsPerLine: Int = 42,
    val maxLines: Int = 2,
    val maxDurationMs: Long = 7000,
    val minDurationMs: Long = 800,
    val targetCpsMin: Int = 17,
    val targetCpsMax: Int = 20
)

data class SubtitleSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val originalText: String? = null,
    val sourceChunk: Int = -1
)

data class Cue(
    val startMs: Long,
    val endMs: Long,
    val lines: List<String>,
    val originalText: String? = null
) {
    val text: String get() = lines.joinToString("\n")
    fun durationMs(): Long = (endMs - startMs).coerceAtLeast(0)
}

/**
 * Turns raw Whisper segments into display-ready captions: 1-2 lines, <= 42
 * chars per line, no overlapping timestamps, minimum reading time, and
 * micro-caption merging.
 */
object CueBuilder {

    fun build(segments: List<SubtitleSegment>, rules: SubtitleRules = SubtitleRules()): List<Cue> {
        val clean = segments
            .filter { it.text.isNotBlank() }
            .sortedBy { it.startMs }

        val out = ArrayList<Cue>(clean.size)
        for (i in clean.indices) {
            val s = clean[i]
            var start = s.startMs.coerceAtLeast(0)
            var end = maxOf(start + rules.minDurationMs, s.endMs)

            // Never overlap the next caption.
            if (i + 1 < clean.size) {
                val nextStart = clean[i + 1].startMs
                if (end > nextStart) {
                    end = maxOf(start + 200, nextStart - 1)
                    if (end <= start) continue
                }
            }
            if (end - start > rules.maxDurationMs) end = start + rules.maxDurationMs

            val lines = wrapText(s.text.trim(), rules)
            if (lines.isEmpty()) continue
            out += Cue(start, end, lines, s.originalText)
        }

        // Merge tiny captions into their neighbour when the result still fits the rules.
        val merged = ArrayList<Cue>(out.size)
        for (cue in out) {
            val prev = merged.lastOrNull()
            val isMicro = cue.durationMs() < 1000 && cue.text.length < 20
            if (
                prev != null && isMicro &&
                prev.lines.size + cue.lines.size <= rules.maxLines &&
                (cue.endMs - prev.startMs) <= rules.maxDurationMs &&
                prev.endMs >= cue.startMs - 100
            ) {
                val joined = wrapText(
                    (prev.lines + cue.lines).joinToString(" "),
                    rules
                )
                merged[merged.size - 1] = prev.copy(
                    lines = joined,
                    endMs = maxOf(prev.endMs, cue.endMs)
                )
            } else {
                merged += cue
            }
        }
        return merged
    }

    fun wrapText(text: String, rules: SubtitleRules): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val joined = words.joinToString(" ")
        if (joined.length <= rules.maxCharsPerLine) return listOf(joined)

        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else current.toString() + " " + word
            if (candidate.length <= rules.maxCharsPerLine) {
                current.clear().append(candidate)
            } else {
                if (current.isNotEmpty()) lines += current.toString()
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()

        if (lines.size > rules.maxLines) {
            // Truncation guard: keep the first lines, hard-split the remainder.
            val kept = lines.take(rules.maxLines - 1).toMutableList()
            val rest = lines.drop(rules.maxLines - 1).joinToString(" ")
            var i = 0
            while (i < rest.length && kept.size < rules.maxLines) {
                val end = minOf(i + rules.maxCharsPerLine, rest.length)
                kept += rest.substring(i, end)
                i = end
            }
            return kept
        }
        return lines
    }
}

object SrtFormatter {
    fun format(cues: List<Cue>): String = buildString {
        cues.forEachIndexed { index, cue ->
            append(index + 1).append('\n')
            append(timestamp(cue.startMs)).append(" --> ").append(timestamp(cue.endMs)).append('\n')
            append(cue.text).append("\n\n")
        }
    }

    fun timestamp(ms: Long, separator: Char = ','): String {
        val h = ms / 3_600_000
        val m = (ms / 60_000) % 60
        val s = (ms / 1000) % 60
        val mil = ms % 1000
        return "%02d:%02d:%02d%c%03d".format(h, m, s, separator, mil)
    }
}

object VttFormatter {
    fun format(cues: List<Cue>): String = buildString {
        append("WEBVTT\n\n")
        cues.forEachIndexed { index, cue ->
            append(index + 1).append('\n')
            append(SrtFormatter.timestamp(cue.startMs, '.'))
            append(" --> ")
            append(SrtFormatter.timestamp(cue.endMs, '.'))
            append('\n')
            append(cue.text).append("\n\n")
        }
    }
}

object TxtFormatter {
    fun format(cues: List<Cue>): String =
        cues.joinToString("\n") { it.lines.joinToString(" ") } + "\n"
}
