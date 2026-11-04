package com.momosubtitle.core.processing

data class ChunkRange(val index: Int, val startMs: Long, val endMs: Long) {
    fun durationMs(): Long = endMs - startMs
}

/**
 * Splits media into fixed chunks (default 30 s). Every chunk carries explicit
 * start/end times so a crash at 73% resumes from the last completed chunk
 * instead of restarting the whole interview.
 */
object ChunkPlanner {

    fun plan(totalMs: Long, chunkSeconds: Int): List<ChunkRange> {
        val chunkMs = chunkSeconds.coerceIn(10, 120) * 1000L
        if (totalMs <= 0) return emptyList()
        val ranges = ArrayList<ChunkRange>()
        var start = 0L
        var index = 0
        while (start < totalMs) {
            val end = minOf(start + chunkMs, totalMs)
            ranges += ChunkRange(index++, start, end)
            start = end
        }
        return ranges
    }
}
