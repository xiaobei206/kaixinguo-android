package com.kadaliao.kaixinguo.update

import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.Intent
import android.app.PendingIntent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

data class UpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val releaseNotes: String,
    val apkName: String,
    val apkUrl: String,
    val sha256: String,
    val fallbackApkUrl: String? = null,
)

data class UpdateInstallStatus(val status: Int, val message: String)

object AppUpdater {
    private const val REPO = "xiaobei206/kaixinguo-android"
    private const val API_RELEASES = "https://api.github.com/repos/$REPO/releases?per_page=100"
    private const val APK_MIRROR_BASE = "https://h.henhei.win/"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val INSTALL_STATUS_PREFS = "update_install_status"
    private const val TAG = "HongguoQuGuangUpdater"
    private val liveInstallConfirmationIntents = ConcurrentHashMap<Int, Intent>()

    const val ACTION_INSTALL_STATE_CHANGED = "com.kadaliao.kaixinguo.UPDATE_INSTALL_STATE_CHANGED"
    const val EXTRA_INSTALL_STATUS = "install_status"
    const val EXTRA_INSTALL_MESSAGE = "install_message"

    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val releases = getJsonArray(API_RELEASES)
        val release = highestStableRelease(releases) ?: return@withContext null
        val releaseAssets = release.optJSONArray("assets") ?: return@withContext null
        var metadataUrl: String? = null
        val downloadUrls = mutableMapOf<String, String>()
        for (i in 0 until releaseAssets.length()) {
            val asset = releaseAssets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.endsWith("-update.json") && url.startsWith("https://")) metadataUrl = url
            if (name.endsWith(".apk") && url.startsWith("https://")) downloadUrls[name] = url
        }
        val url = metadataUrl ?: return@withContext null
        val metadata = getJson(url)
        val latestCode = metadata.optLong("versionCode", -1)
        val currentCode = if (Build.VERSION.SDK_INT >= 28) {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
        }
        if (latestCode <= currentCode) return@withContext null

        val assets = metadata.optJSONArray("assets") ?: return@withContext null
        val byName = mutableMapOf<String, Pair<String, String>>()
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            val hash = asset.optString("sha256").lowercase()
            if (name.endsWith(".apk") && hash.matches(Regex("[0-9a-f]{64}"))) {
                byName[name] = hash to (downloadUrls[name] ?: "")
            }
        }
        val abiSuffixes = Build.SUPPORTED_ABIS.mapNotNull { abi ->
            when (abi) {
                "arm64-v8a" -> "-arm64-v8a.apk"
                "armeabi-v7a" -> "-armeabi-v7a.apk"
                "x86_64" -> "-x86_64.apk"
                else -> null
            }
        }
        val selected = abiSuffixes.firstNotNullOfOrNull { suffix ->
            byName.entries.firstOrNull { it.key.endsWith(suffix) && it.value.second.isNotBlank() }
        } ?: byName.entries.firstOrNull { it.key.endsWith("-universal.apk") && it.value.second.isNotBlank() }
            ?: return@withContext null

        UpdateInfo(
            versionName = metadata.optString("versionName", release.optString("tag_name").removePrefix("v")),
            versionCode = latestCode,
            releaseNotes = release.optString("body").take(1800),
            apkName = selected.key,
            apkUrl = mirrorApkUrl(selected.key),
            sha256 = selected.value.first,
            fallbackApkUrl = selected.value.second,
        )
    }

    /**
     * GitHub's /releases/latest can choose unpredictably when releases share
     * the same created_at timestamp. Compare stable vMAJOR.MINOR.PATCH tags instead.
     */
    internal fun highestStableRelease(releases: org.json.JSONArray): JSONObject? {
        val candidates = mutableListOf<Pair<JSONObject, List<Int>>>()
        for (index in 0 until releases.length()) {
            val release = releases.optJSONObject(index) ?: continue
            if (release.optBoolean("draft", true) || release.optBoolean("prerelease", true)) continue
            val version = parseReleaseVersion(release.optString("tag_name")) ?: continue
            candidates += release to version
        }
        return candidates.maxWithOrNull { left, right ->
            val versionOrder = compareVersionParts(left.second, right.second)
            if (versionOrder != 0) {
                versionOrder
            } else {
                left.first.optString("published_at").compareTo(right.first.optString("published_at"))
            }
        }?.first
    }

    internal fun compareReleaseVersions(first: String, second: String): Int {
        val left = parseReleaseVersion(first) ?: return first.compareTo(second)
        val right = parseReleaseVersion(second) ?: return first.compareTo(second)
        return compareVersionParts(left, right)
    }

    private fun parseReleaseVersion(tag: String): List<Int>? {
        val match = Regex("""^v?([0-9]+)[.]([0-9]+)[.]([0-9]+)$""").matchEntire(tag) ?: return null
        return listOf(
            match.groupValues[1].toIntOrNull() ?: return null,
            match.groupValues[2].toIntOrNull() ?: return null,
            match.groupValues[3].toIntOrNull() ?: return null,
        )
    }

    private fun compareVersionParts(first: List<Int>, second: List<Int>): Int {
        for (index in 0..2) {
            val comparison = first[index].compareTo(second[index])
            if (comparison != 0) return comparison
        }
        return 0
    }

    /**
     * Returns a cached APK only when it still matches the release's published digest.
     * This lets an install retry reuse a verified download without trusting stale cache files.
     */
    suspend fun findVerifiedCachedApk(context: Context, info: UpdateInfo): File? =
        withContext(Dispatchers.IO) {
            val target = File(File(context.cacheDir, "updates"), info.apkName)
            if (!target.isFile || target.length() <= 0L) return@withContext null
            if (!sha256(target).equals(info.sha256, ignoreCase = true)) {
                target.delete()
                return@withContext null
            }
            target
        }

    internal fun mirrorApkUrl(apkName: String): String = "$APK_MIRROR_BASE$apkName"

    /** Prefer the official GitHub release asset for external browser downloads, not the mirror. */
    internal fun browserDownloadUrl(info: UpdateInfo): String =
        info.fallbackApkUrl?.takeIf { it.startsWith("https://") } ?: info.apkUrl

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            findVerifiedCachedApk(context, info)?.let { return@withContext it }
            val folder = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(folder, info.apkName)
            val partial = File(folder, info.apkName + ".part")
            val urls = listOfNotNull(info.apkUrl, info.fallbackApkUrl)
                .filter { it.isNotBlank() }
                .distinct()
            check(urls.isNotEmpty()) { "没有可用的下载地址" }
            var lastFailure: Exception? = null

            for ((index, address) in urls.withIndex()) {
                partial.delete()
                var connection: HttpURLConnection? = null
                try {
                    connection = (URL(address).openConnection() as HttpURLConnection).apply {
                        connectTimeout = CONNECT_TIMEOUT_MS
                        readTimeout = READ_TIMEOUT_MS
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "HongguoQuGuang-Android")
                        setRequestProperty("Accept", "application/octet-stream")
                    }
                    connection.connect()
                    if (connection.responseCode !in 200..299) error("下载失败：HTTP ${connection.responseCode}")
                    val total = connection.contentLengthLong
                    var received = 0L
                    connection.inputStream.use { input ->
                        partial.outputStream().buffered().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                received += count
                                if (total > 0) onProgress(((received * 100) / total).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                    if (total > 0L && received != total) {
                        error("下载不完整：收到 $received / $total 字节")
                    }
                    val actual = sha256(partial)
                    check(actual.equals(info.sha256, ignoreCase = true)) { "安装包 SHA-256 校验失败，已取消安装" }
                    target.delete()
                    check(partial.renameTo(target)) { "无法保存已校验的安装包" }
                    return@withContext target
                } catch (e: CancellationException) {
                    partial.delete()
                    throw e
                } catch (e: Exception) {
                    partial.delete()
                    lastFailure = e
                    if (index < urls.lastIndex) onProgress(0)
                } finally {
                    connection?.disconnect()
                }
            }
            throw lastFailure ?: IllegalStateException("下载失败：没有可用的下载地址")
        }

    fun install(context: Context, apk: File): Int {
        check(apk.isFile && apk.length() > 0L) { "安装包不存在或为空" }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        params.setSize(apk.length())
        // Always ask Android to show its normal install confirmation UI on API 31+.
        // USER_ACTION_NOT_REQUIRED is conditional on installer/update-owner eligibility and
        // has caused some OEM installers to abort before surfacing the confirmation screen.
        val requireUserAction = installUserActionRequirement(Build.VERSION.SDK_INT)
        if (requireUserAction != null) params.setRequireUserAction(requireUserAction)
        Log.i(
            TAG,
            "Preparing APK install: manufacturer=${Build.MANUFACTURER}, model=${Build.MODEL}, " +
                "sdk=${Build.VERSION.SDK_INT}, apkBytes=${apk.length()}, " +
                "userAction=${requireUserAction?.toString() ?: "platform-default"}",
        )
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().buffered().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output, 64 * 1024)
                        session.fsync(output)
                    }
                }
                val callback = Intent(context, UpdateInstallReceiver::class.java)
                    .setAction(UpdateInstallReceiver.ACTION_INSTALL_STATUS)
                    .putExtra(UpdateInstallReceiver.EXTRA_SESSION_ID, sessionId)
                val pending = PendingIntent.getBroadcast(
                    context, sessionId, callback,
                    installStatusPendingIntentFlags(Build.VERSION.SDK_INT),
                )
                Log.i(TAG, "Committing APK install sessionId=$sessionId")
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
        return sessionId
    }

    /** Abandon an older session when the user explicitly retries a missing confirmation screen. */
    fun abandonInstallSession(context: Context, sessionId: Int) {
        if (sessionId < 0) return
        runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
            .onFailure { Log.w(TAG, "Could not abandon previous install session $sessionId", it) }
    }

    private fun installConfirmationPreferenceKey(sessionId: Int) = "confirmation_$sessionId"

    /** Keep the exact callback Intent in memory; the URI copy is only a process-death fallback. */
    fun saveInstallConfirmation(context: Context, sessionId: Int, confirmation: Intent) {
        require(sessionId >= 0)
        liveInstallConfirmationIntents[sessionId] = Intent(confirmation)
        val serialized = confirmation.toUri(Intent.URI_INTENT_SCHEME)
        context.getSharedPreferences(INSTALL_STATUS_PREFS, Context.MODE_PRIVATE).edit()
            .putString(installConfirmationPreferenceKey(sessionId), serialized)
            .commit()
        Log.i(
            TAG,
            "Saved PackageInstaller confirmation for foreground launch; sessionId=$sessionId " +
                "action=${confirmation.action} component=${confirmation.component?.flattenToShortString()} " +
                "data=${confirmation.data} flags=0x${confirmation.flags.toString(16)} " +
                "extras=${confirmation.extras?.keySet()?.sorted()?.joinToString()}",
        )
    }

    fun loadInstallConfirmation(context: Context, sessionId: Int): Intent? {
        if (sessionId < 0) return null
        liveInstallConfirmationIntents[sessionId]?.let {
            Log.i(TAG, "Using original in-memory PackageInstaller confirmation Intent for sessionId=$sessionId")
            return Intent(it)
        }
        val serialized = context.getSharedPreferences(INSTALL_STATUS_PREFS, Context.MODE_PRIVATE)
            .getString(installConfirmationPreferenceKey(sessionId), null) ?: return null
        return runCatching { Intent.parseUri(serialized, Intent.URI_INTENT_SCHEME) }
            .onSuccess { Log.w(TAG, "Restoring serialized PackageInstaller confirmation after process recreation; sessionId=$sessionId") }
            .onFailure { Log.e(TAG, "Could not restore PackageInstaller confirmation for sessionId=$sessionId", it) }
            .getOrNull()
    }

    internal fun installStatusDetail(
        message: String,
        otherPackage: String,
        developerVerificationReason: String,
    ): String = listOfNotNull(
        message.takeIf { it.isNotBlank() },
        otherPackage.takeIf { it.isNotBlank() }?.let { "阻止安装的包：$it" },
        developerVerificationReason.takeIf { it.isNotBlank() }?.let { "开发者验证失败原因：$it" },
    ).joinToString("；")

    fun readInstallStatus(context: Context, sessionId: Int): UpdateInstallStatus? {
        val prefs = context.getSharedPreferences(INSTALL_STATUS_PREFS, Context.MODE_PRIVATE)
        val key = "status_$sessionId"
        if (!prefs.contains(key)) return null
        return UpdateInstallStatus(
            status = prefs.getInt(key, PackageInstaller.STATUS_FAILURE),
            message = prefs.getString("message_$sessionId", "").orEmpty(),
        )
    }

    /** Persists the system callback before notifying the foreground Activity. */
    fun recordInstallStatus(context: Context, sessionId: Int, status: Int, message: String) {
        if (sessionId < 0) return
        val statusPrefs = context.getSharedPreferences(INSTALL_STATUS_PREFS, Context.MODE_PRIVATE)
        val statusEditor = statusPrefs.edit()
            .putInt("status_$sessionId", status)
            .putString("message_$sessionId", message)
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
            statusEditor.remove(installConfirmationPreferenceKey(sessionId))
            liveInstallConfirmationIntents.remove(sessionId)
        }
        statusEditor.commit()

        context.sendBroadcast(
            Intent(ACTION_INSTALL_STATE_CHANGED)
                .setPackage(context.packageName)
                .putExtra(UpdateInstallReceiver.EXTRA_SESSION_ID, sessionId)
                .putExtra(EXTRA_INSTALL_STATUS, status)
                .putExtra(EXTRA_INSTALL_MESSAGE, message),
        )
    }

    fun installFailureMessage(status: Int, message: String): String {
        val reason = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> "系统安装已取消"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "系统阻止安装"
            PackageInstaller.STATUS_FAILURE_CONFLICT -> "与已安装版本冲突"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "安装包与设备不兼容"
            PackageInstaller.STATUS_FAILURE_INVALID -> "安装包无效"
            PackageInstaller.STATUS_FAILURE_STORAGE -> "存储空间不足"
            else -> "系统安装失败"
        }
        return if (message.isBlank()) "$reason（状态码 $status）" else "$reason：$message（状态码 $status）"
    }


/**
 * Android 12+ must follow the explicit system confirmation flow. Older Android
 * versions do not expose setRequireUserAction(), so their platform default applies.
 */
internal fun installUserActionRequirement(sdkInt: Int): Int? =
    if (sdkInt >= Build.VERSION_CODES.S) {
        PackageInstaller.SessionParams.USER_ACTION_REQUIRED
    } else {
        null
    }

/**
 * PackageInstaller.commit() must be able to add status extras to the callback
 * Intent. From Android 12 (API 31), its PendingIntent therefore must be mutable.
 * The callback targets our non-exported receiver explicitly.
 */
internal fun installStatusPendingIntentFlags(sdkInt: Int): Int {
    val mutabilityFlag = if (sdkInt >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_MUTABLE
    } else {
        0
    }
    return PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
}

    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    private fun getJsonArray(address: String): org.json.JSONArray {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "HongguoQuGuang-Android")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        try {
            check(connection.responseCode in 200..299) { "GitHub 更新检查失败：HTTP ${connection.responseCode}" }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { org.json.JSONArray(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }

    private fun getJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "HongguoQuGuang-Android")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        try {
            check(connection.responseCode in 200..299) { "GitHub 更新检查失败：HTTP ${connection.responseCode}" }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }
}
