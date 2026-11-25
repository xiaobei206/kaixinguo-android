package com.kadaliao.kaixinguo.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryIndicatorColorTest {
    @Test
    fun levelColorUsesRedYellowGreenThresholds() {
        assertEquals(Color(0xFFF44336), batteryLevelColor(0))
        assertEquals(Color(0xFFF44336), batteryLevelColor(19))
        assertEquals(Color(0xFFFFC107), batteryLevelColor(20))
        assertEquals(Color(0xFFFFC107), batteryLevelColor(39))
        assertEquals(Color(0xFF4CAF50), batteryLevelColor(40))
        assertEquals(Color(0xFF4CAF50), batteryLevelColor(100))
    }

    @Test
    fun chargingUsesDistinctBlueIconColor() {
        assertEquals(Color(0xFF29B6F6), batteryIconColor(80, charging = true))
        assertEquals(Color(0xFF4CAF50), batteryIconColor(80, charging = false))
        assertEquals(Color(0xFF29B6F6), batteryIconColor(10, charging = true))
    }

    @Test
    fun outOfRangeLevelsAreClampedForColorSelection() {
        assertEquals(batteryLevelColor(0), batteryLevelColor(-5))
        assertEquals(batteryLevelColor(100), batteryLevelColor(140))
    }
}
