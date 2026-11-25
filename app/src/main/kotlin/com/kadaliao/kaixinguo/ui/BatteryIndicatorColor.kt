package com.kadaliao.kaixinguo.ui

import androidx.compose.ui.graphics.Color

/**
 * Colors the battery by remaining charge when unplugged, and uses a distinct blue while charging.
 * The separate level color remains available for the percentage label so low charge stays visible.
 */
internal fun batteryLevelColor(percent: Int): Color = when {
    percent.coerceIn(0, 100) < 20 -> Color(0xFFF44336)
    percent.coerceIn(0, 100) < 40 -> Color(0xFFFFC107)
    else -> Color(0xFF4CAF50)
}

internal fun batteryIconColor(percent: Int, charging: Boolean): Color =
    if (charging) Color(0xFF29B6F6) else batteryLevelColor(percent)
