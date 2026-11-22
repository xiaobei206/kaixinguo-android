package com.kadaliao.kaixinguo.update

import android.app.PendingIntent
import android.content.pm.PackageInstaller
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpdaterTest {
    @Test
    fun installStatusPendingIntentAddsMutabilityFlagOnAndroid12AndLater() {
        val legacyFlags = AppUpdater.installStatusPendingIntentFlags(Build.VERSION_CODES.R)
        val modernFlags = AppUpdater.installStatusPendingIntentFlags(Build.VERSION_CODES.S)

        // Legacy Android relies on the pre-S default mutability behavior.
        assertEquals(PendingIntent.FLAG_UPDATE_CURRENT, legacyFlags)
        // API 31+ must explicitly be mutable so PackageInstaller can report status.
        assertEquals(PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE, modernFlags)
    }

    @Test
    fun blockedInstallFailureIncludesSystemReasonAndCode() {
        assertEquals(
            "系统阻止安装：INSTALL_FAILED_BLOCKED（状态码 ${PackageInstaller.STATUS_FAILURE_BLOCKED}）",
            AppUpdater.installFailureMessage(PackageInstaller.STATUS_FAILURE_BLOCKED, "INSTALL_FAILED_BLOCKED"),
        )
    }

    @Test
    fun installStatusPendingIntentKeepsPreAndroid12Compatibility() {
        val flags = AppUpdater.installStatusPendingIntentFlags(Build.VERSION_CODES.R)

        assertEquals(PendingIntent.FLAG_UPDATE_CURRENT, flags)
    }
}
