package com.kadaliao.kaixinguo.model

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import dev.openhongguo.ErrorCode
import java.util.Locale
import dev.openhongguo.OpenhongguoException
import java.time.Instant
import java.util.ArrayDeque

data class SdkApiTrace(
    val time: String,
    val message: String,
    val method: String? = null,
    val endpoint: String? = null,
    val httpStatus: Int? = null,
)

object SdkDiagnostics {
    private const val MAX_EVENTS = 128
    private val lock = Any()
    private val events = ArrayDeque<SdkApiTrace>()
    @Volatile private var deviceEnvironment = "设备环境尚未初始化"
    private val httpLine = Regex("""^HTTP\s+(\S+)\s+(https?://\S+)\s+->\s+(\d+)\s*$""")

    fun initialize(context: Context) {
        deviceEnvironment = runCatching {
            val app = context.applicationContext
            val manager = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memory = ActivityManager.MemoryInfo()
            manager?.getMemoryInfo(memory)
            val storage = StatFs(Environment.getDataDirectory().absolutePath)
            val actualDevice = listOf(Build.MANUFACTURER, Build.MODEL, Build.PRODUCT)
                .filter { it.isNotBlank() }.distinct().joinToString(" / ")
            val ram = manager?.let { gib(memory.totalMem) } ?: "不可读取"
            "实际设备=" + actualDevice +
                "；Android=" + Build.VERSION.RELEASE + " API=" + Build.VERSION.SDK_INT +
                "；ABI=" + Build.SUPPORTED_ABIS.joinToString(",") +
                "；RAM=" + ram +
                "；内部存储总量=" + gib(storage.totalBytes) + " 可用=" + gib(storage.availableBytes) +
                "；SDK 固定上报模板=Xiaomi/24129PN74C Android15 API35 Build/AQ3A.240812.002 1200*2670 DPI460 channel=xiaomi_8662_64"
        }.getOrElse { "设备环境读取失败：" + it.javaClass.simpleName }
    }

    private fun gib(bytes: Long): String =
        String.format(Locale.US, "%.1f GiB", bytes.toDouble() / (1024.0 * 1024.0 * 1024.0))

    fun environmentSummary(): String = deviceEnvironment

    fun record(line: String) {
        val safeLine = line.trim().take(600)
        val match = httpLine.matchEntire(safeLine)
        val isDeviceRotation = safeLine.contains("anonymous device unusable", ignoreCase = true) ||
            safeLine.contains("registering a new anonymous device", ignoreCase = true)
        val isProfileEvent = safeLine.contains("anonymous profile", ignoreCase = true) ||
            safeLine.contains("anonymous registration", ignoreCase = true)
        val isPlaybackEvent = safeLine.startsWith("playback ", ignoreCase = true)
        if (match == null && !isDeviceRotation && !isProfileEvent && !isPlaybackEvent) return

        val url = match?.groupValues?.get(2)?.substringBefore('?')
        val event = SdkApiTrace(
            time = Instant.now().toString(),
            message = safeLine,
            method = match?.groupValues?.get(1),
            endpoint = url?.substringAfter("://"),
            httpStatus = match?.groupValues?.get(3)?.toIntOrNull(),
        )
        synchronized(lock) {
            events.addLast(event)
            while (events.size > MAX_EVENTS) events.removeFirst()
        }
    }

    fun snapshot(): List<SdkApiTrace> = synchronized(lock) { events.toList() }
}

data class PlaybackFailure(
    val occurredAt: String,
    val episodeId: String,
    val sdkCode: String,
    val sdkMessage: String,
    val serverBusinessCode: String?,
    val serverErrorName: String?,
    val serverErrorMessage: String?,
    val httpStatus: Int?,
    val requestMethod: String?,
    val endpoint: String?,
    val responseBytes: Int?,
    val decodedResponseBytes: Int?,
    val responseContentType: String?,
    val responseContentEncoding: String?,
    val responseBodyNote: String,
    val retryable: Boolean,
    val retryAfterMs: Long,
    val events: List<SdkApiTrace>,
) {
    val canRefreshDevice: Boolean
        get() = sdkCode == ErrorCode.ANONYMOUS_LIMITED.name || sdkCode == ErrorCode.DEVICE_REJECTED.name

    fun toReport(): String = buildString {
        appendLine("开心果 Android 播放故障诊断")
        appendLine("时间：" + occurredAt)
        appendLine("Episode ID：" + episodeId)
        appendLine("SDK 错误代码：" + sdkCode)
        appendLine("SDK 错误信息：" + sdkMessage)
        appendLine("服务器原始业务码：" + (serverBusinessCode ?: "服务器未提供或 SDK 未取得"))
        appendLine("服务器业务码名称：" + (serverErrorName ?: "未取得"))
        appendLine("服务器原始错误信息：" + (serverErrorMessage ?: "未取得"))
        appendLine("HTTP 状态码：" + (httpStatus?.toString() ?: "未取得"))
        appendLine("请求方法：" + (requestMethod ?: "未取得"))
        appendLine("接口路径：" + (endpoint ?: "未取得"))
        appendLine("响应字节数（传输）：" + (responseBytes?.toString() ?: "SDK 未提供"))
        appendLine("响应字节数（解压后）：" + (decodedResponseBytes?.toString() ?: "SDK 未提供"))
        appendLine("Content-Type：" + (responseContentType ?: "未提供"))
        appendLine("Content-Encoding：" + (responseContentEncoding ?: "未提供"))
        appendLine("响应体说明：" + responseBodyNote)
        appendLine("SDK 建议可重试：" + retryable)
        appendLine("SDK 建议等待：" + retryAfterMs + " ms")
        appendLine("手机和设备环境：" + SdkDiagnostics.environmentSummary())
        appendLine("隐私说明：匿名标识只显示短摘要；不记录原始设备 ID、Cookie、Token、签名或完整响应体。")
        appendLine("最近 SDK 请求记录：")
        if (events.isEmpty()) appendLine("（无）")
        events.forEach { appendLine(it.time + "  " + it.message) }
    }

    companion object {
        private val businessCode = Regex("""\(code\s+([^,]+),\s+([^)]+)\)$""")
        private val httpStatusInMessage = Regex("""HTTP\s+(\d{3})""")

        private fun OpenhongguoException.stringExtra(property: String): String? {
            val getter = "get" + property.replaceFirstChar { it.uppercase() }
            return runCatching {
                javaClass.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }
                    ?.invoke(this) as? String
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }

        private fun OpenhongguoException.intExtra(property: String): Int? {
            val getter = "get" + property.replaceFirstChar { it.uppercase() }
            return runCatching {
                javaClass.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }
                    ?.invoke(this) as? Int
            }.getOrNull()
        }

        fun from(error: Throwable, episodeId: String, recentEvents: List<SdkApiTrace>): PlaybackFailure {
            val sdkError = error as? OpenhongguoException
            val sdkMessage = error.message ?: error.toString()
            val sdkCode = sdkError?.code?.name ?: error.javaClass.simpleName
            val businessMatch = businessCode.find(sdkMessage)
            val legacyCode = businessMatch?.groupValues?.get(1)?.trim()
            val legacyName = businessMatch?.groupValues?.get(2)?.trim()
            val legacyMessage = if (businessMatch != null) {
                sdkMessage.substringAfter("openhongguo: ", sdkMessage).substringBeforeLast(" (code ")
            } else null
            val relevantTrace = recentEvents.lastOrNull {
                it.httpStatus != null && it.endpoint?.contains("/player/", true) == true
            } ?: recentEvents.lastOrNull { it.httpStatus != null }

            val serverCode = sdkError?.stringExtra("serverCode") ?: legacyCode
            val serverName = sdkError?.stringExtra("serverName") ?: legacyName
            val serverMessage = sdkError?.stringExtra("serverMessage") ?: legacyMessage
            val httpStatus = sdkError?.intExtra("httpStatus")
                ?: httpStatusInMessage.find(sdkMessage)?.groupValues?.get(1)?.toIntOrNull()
                ?: relevantTrace?.httpStatus
            val requestMethod = sdkError?.stringExtra("requestMethod") ?: relevantTrace?.method
            val endpoint = sdkError?.stringExtra("requestPath") ?: relevantTrace?.endpoint
            val responseBytes = sdkError?.intExtra("responseBytes")
            val decodedResponseBytes = sdkError?.intExtra("decodedResponseBytes")
            val responseContentType = sdkError?.stringExtra("responseContentType")
            val responseContentEncoding = sdkError?.stringExtra("responseContentEncoding")

            val bodyNote = when {
                sdkError?.code == ErrorCode.ANONYMOUS_LIMITED && httpStatus != null ->
                    "服务器没有提供业务错误码或错误文本；响应体传输 " +
                        (responseBytes?.toString() ?: "未知") + " bytes，解压后 " +
                        (decodedResponseBytes?.toString() ?: "未知") + " bytes。"
                sdkError?.code == ErrorCode.ANONYMOUS_LIMITED ->
                    "服务器响应体为空；当前 AAR 未提供 HTTP 状态和响应字节数，升级 SDK 后将显示实际元数据。"
                serverCode != null || serverMessage != null ->
                    "已从服务器错误响应中提取并保留业务码和原始 message；未复制完整响应体，避免混入设备凭据或其他敏感字段。"
                else ->
                    "当前 SDK 没有提供可识别的服务器业务码；请结合 HTTP 状态、接口路径和 SDK 错误判断。"
            }

            return PlaybackFailure(
                occurredAt = Instant.now().toString(),
                episodeId = episodeId,
                sdkCode = sdkCode,
                sdkMessage = sdkMessage,
                serverBusinessCode = serverCode,
                serverErrorName = serverName,
                serverErrorMessage = serverMessage,
                httpStatus = httpStatus,
                requestMethod = requestMethod,
                endpoint = endpoint,
                responseBytes = responseBytes,
                decodedResponseBytes = decodedResponseBytes,
                responseContentType = responseContentType,
                responseContentEncoding = responseContentEncoding,
                responseBodyNote = bodyNote,
                retryable = sdkError?.retryable ?: false,
                retryAfterMs = sdkError?.retryAfterMs ?: 0L,
                events = recentEvents,
            )
        }
    }
}
