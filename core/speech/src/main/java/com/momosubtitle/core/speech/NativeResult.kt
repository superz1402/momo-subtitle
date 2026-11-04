package com.momosubtitle.core.speech

import kotlinx.serialization.Serializable

@Serializable
data class NativeSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

@Serializable
data class NativeResult(
    val language: String = "unknown",
    val durationMs: Long = 0,
    val segments: List<NativeSegment> = emptyList()
)
