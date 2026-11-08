package com.momosubtitle.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Design tokens (UI rehaul issue): spacing scale, corner radii and motion
 * durations shared by every screen. Derived from the owner-app design study
 * (Your-Files / nexpdf / tabit / HeyYou) — see worklog 2026-11-08.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val huge = 32.dp
}

object Radius {
    val card = 16.dp
    val sheet = 20.dp
    val pill = 50
}

/** Motion durations in ms (fast feedback / standard transitions / hero moments). */
object Motion {
    const val fast = 120
    const val normal = 220
    const val slow = 320
}
