package com.kadaliao.kaixinguo.update

import android.app.PendingIntent
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {
    @Test
    fun installStatusPendingIntentIsMutableOnAndroid12AndLater() {
        val flags = AppUpdater.installStatusPendingIntentFlags(Build.VERSION_CODES.S)

        assertTrue(flags and PendingIntent.FLAG_UPDATE_CURRENT != 0)
        assertTrue(flags and PendingIntent.FLAG_MUTABLE != 0)
        assertFalse(flags and PendingIntent.FLAG_IMMUTABLE != 0)
    }

    @Test
    fun installStatusPendingIntentKeepsPreAndroid12Compatibility() {
        val flags = AppUpdater.installStatusPendingIntentFlags(Build.VERSION_CODES.R)

        assertTrue(flags and PendingIntent.FLAG_UPDATE_CURRENT != 0)
        assertFalse(flags and PendingIntent.FLAG_IMMUTABLE != 0)
        assertEquals(PendingIntent.FLAG_UPDATE_CURRENT, flags)
    }
}
