package com.kadaliao.kaixinguo.update

import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.Intent
import android.app.PendingIntent
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class UpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val releaseNotes: String,
    val apkName: String,
    val apkUrl: String,
    val sha256: String,
)

object AppUpdater {
    private const val REPO = "xiaobei206/kaixinguo-android"
    private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000

    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val release = getJson(API_LATEST)
        if (release.optBoolean("draft", true) || release.optBoolean("prerelease", true)) return@withContext null
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
            apkUrl = selected.value.second,
            sha256 = selected.value.first,
        )
    }

    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val folder = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(folder, info.apkName)
            val partial = File(folder, info.apkName + ".part")
            partial.delete()
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
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
                val digest = MessageDigest.getInstance("SHA-256")
                partial.inputStream().buffered().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                check(actual.equals(info.sha256, ignoreCase = true)) { "安装包 SHA-256 校验失败，已取消安装" }
                target.delete()
                check(partial.renameTo(target)) { "无法保存已校验的安装包" }
                target
            } catch (e: Exception) {
                partial.delete()
                throw e
            } finally {
                connection?.disconnect()
            }
        }

    fun install(context: Context, apk: File) {
        check(apk.isFile && apk.length() > 0L) { "安装包不存在或为空" }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        params.setSize(apk.length())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
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
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }

    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

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
