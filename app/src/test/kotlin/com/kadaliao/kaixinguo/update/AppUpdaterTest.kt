package com.kadaliao.kaixinguo.update

import android.app.PendingIntent
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {
    @Test
    fun installStatusPendingIntentAddsMutabilityFlagOnAndroid12AndLater() {
        val legacyFlags = AppUpdater.installStatusPendingIntentFlags(Build.VERSION_CODES.R)
        val modernFlags = AppUpdater.installStatusPendingIntentFlags(Build.VERSION_CODES.S)

        // Legacy Android relies on the pre-S default mutability behavior.
        assertEquals(PendingIntent.FLAG_UPDATE_CURRENT, legacyFlags)
        // API 31+ must add a mutability flag so PackageInstaller can report status.
        assertTrue(modernFlags != legacyFlags)
    }

    @Test
    fun installStatusPendingIntentKeepsPreAndroid12Compatibility() {
        val flags = AppUpdater.installStatusPendingIntentFlags(Build.VERSION_CODES.R)

        assertEquals(PendingIntent.FLAG_UPDATE_CURRENT, flags)
    }
}
