package com.momosubtitle.core.common

/** Time helpers shared by UI and exporters. */
object TimeFormat {

    /** 42:18 for short media, 1:12:35 for long-form. */
    fun duration(totalSeconds: Double): String {
        val s = totalSeconds.toLong().coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    /** SRT-style stamp: 00:00:02,100 */
    fun stamp(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms / 60_000) % 60
        val s = (ms / 1000) % 60
        val mil = ms % 1000
        return "%02d:%02d:%02d,%03d".format(h, m, s, mil)
    }

    fun humanBytes(bytes: Long): String = when {
        bytes >= 1 shl 30 -> "%.1f GB".format(bytes.toDouble() / (1 shl 30))
        bytes >= 1 shl 20 -> "%.1f MB".format(bytes.toDouble() / (1 shl 20))
        bytes >= 1 shl 10 -> "%.1f KB".format(bytes.toDouble() / (1 shl 10))
        else -> "$bytes B"
    }
}
