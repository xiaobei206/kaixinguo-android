package com.kadaliao.kaixinguo.update

import android.app.PendingIntent
import android.content.pm.PackageInstaller
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpdaterTest {
    @Test
    fun apkDownloadsUseTheCustomMirrorRootAndKeepVersionedNames() {
        assertEquals(
            "https://h.henhei.win/kaixinguo-android-v0.4.27-arm64-v8a.apk",
            AppUpdater.mirrorApkUrl("kaixinguo-android-v0.4.27-arm64-v8a.apk"),
        )
    }

    @Test
    fun releaseSelectionUsesHighestStableVersionEvenWhenOlderReleaseWasPublishedLater() {
        val releases = org.json.JSONArray()
            .put(
                org.json.JSONObject()
                    .put("tag_name", "v0.4.30")
                    .put("draft", false)
                    .put("prerelease", false)
                    .put("published_at", "2026-11-24T13:10:34Z"),
            )
            .put(
                org.json.JSONObject()
                    .put("tag_name", "v0.4.29")
                    .put("draft", false)
                    .put("prerelease", false)
                    .put("published_at", "2026-11-24T13:11:38Z"),
            )
            .put(
                org.json.JSONObject()
                    .put("tag_name", "v0.4.99")
                    .put("draft", false)
                    .put("prerelease", true),
            )

        assertEquals("v0.4.30", AppUpdater.highestStableRelease(releases)?.optString("tag_name"))
    }

    @Test
    fun releaseVersionComparisonUsesNumericSegments() {
        assertEquals(1, AppUpdater.compareReleaseVersions("v0.4.30", "v0.4.29"))
        assertEquals(-1, AppUpdater.compareReleaseVersions("v0.10.0", "v0.11.0"))
        assertEquals(1, AppUpdater.compareReleaseVersions("1.0.0", "0.99.99"))
    }

    @Test
    fun installAlwaysRequiresExplicitConfirmationOnAndroid12AndLater() {
        assertEquals(
            PackageInstaller.SessionParams.USER_ACTION_REQUIRED,
            AppUpdater.installUserActionRequirement(Build.VERSION_CODES.S),
        )
        assertEquals(
            PackageInstaller.SessionParams.USER_ACTION_REQUIRED,
            AppUpdater.installUserActionRequirement(Build.VERSION_CODES.S + 1),
        )
        assertEquals(null, AppUpdater.installUserActionRequirement(Build.VERSION_CODES.R))
    }

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
    fun abortedInstallFailureKeepsTheSystemPermissionRejectionReason() {
        assertEquals(
            "系统安装已取消：User rejected permissions（状态码 ${PackageInstaller.STATUS_FAILURE_ABORTED}）",
            AppUpdater.installFailureMessage(PackageInstaller.STATUS_FAILURE_ABORTED, "User rejected permissions"),
        )
    }

    @Test
    fun installStatusPendingIntentKeepsPreAndroid12Compatibility() {
        val flags = AppUpdater.installStatusPendingIntentFlags(Build.VERSION_CODES.R)

        assertEquals(PendingIntent.FLAG_UPDATE_CURRENT, flags)
    }
}
