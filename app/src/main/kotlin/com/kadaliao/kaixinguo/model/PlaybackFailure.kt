package com.kadaliao.kaixinguo.model

import dev.openhongguo.ErrorCode
import dev.openhongguo.OpenhongguoException
import java.time.Instant
import java.util.ArrayDeque

/** One safe, timestamped SDK diagnostic event. URLs come from the SDK's redacted logger. */
data class SdkApiTrace(
    val time: String,
    val message: String,
    val method: String? = null,
    val endpoint: String? = null,
    val httpStatus: Int? = null,
)

/**
 * Keeps only a small in-memory ring of SDK HTTP/status and anonymous-device
 * rotation events. No request headers, cookies, signatures or response bodies
 * are collected here.
 */
object SdkDiagnostics {
    private const val MAX_EVENTS = 96
    private val lock = Any()
    private val events = ArrayDeque<SdkApiTrace>()
    private val httpLine = Regex("""^HTTP\\s+(\\S+)\\s+(https?://\\S+)\\s+->\\s+(\\d+)\\s*$""")

    fun record(line: String) {
        val safeLine = line.trim().take(600)
        val match = httpLine.matchEntire(safeLine)
        val isDeviceRotation = safeLine.contains("anonymous device unusable", ignoreCase = true) ||
            safeLine.contains("registering a new anonymous device", ignoreCase = true)
        if (match == null && !isDeviceRotation) return

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

/** What the player can honestly report from the current Android SDK surface. */
data class PlaybackFailure(
    val occurredAt: String,
    val episodeId: String,
    val sdkCode: String,
    val sdkMessage: String,
    val serverBusinessCode: String?,
    val serverErrorName: String?,
    val serverErrorMessage: String?,
    val httpStatus: Int?,
    val endpoint: String?,
    val responseBodyNote: String,
    val retryable: Boolean,
    val retryAfterMs: Long,
    val events: List<SdkApiTrace>,
) {
    val canRefreshDevice: Boolean
        get() = sdkCode == ErrorCode.ANONYMOUS_LIMITED.name || sdkCode == ErrorCode.DEVICE_REJECTED.name

    fun toReport(): String = buildString {
        appendLine("开心果 Android 播放故障诊断")
        appendLine("时间：$occurredAt")
        appendLine("Episode ID：$episodeId")
        appendLine("SDK 错误代码：$sdkCode")
        appendLine("SDK 错误信息：$sdkMessage")
        appendLine("服务器原始业务码：${serverBusinessCode ?: "未取得"}")
        appendLine("服务器业务码名称：${serverErrorName ?: "未取得"}")
        appendLine("服务器原始错误信息：${serverErrorMessage ?: "未取得"}")
        appendLine("HTTP 状态码：${httpStatus ?: "未取得"}")
        appendLine("接口：${endpoint ?: "未取得"}")
        appendLine("原始响应体说明：$responseBodyNote")
        appendLine("SDK 建议可重试：$retryable")
        appendLine("SDK 建议等待：${retryAfterMs} ms")
        appendLine("最近 SDK 请求记录：")
        if (events.isEmpty()) appendLine("（无）")
        events.forEach { appendLine("${it.time}  ${it.message}") }
    }

    companion object {
        private val businessCode = Regex("""\\(code\\s+([^,]+),\\s+([^)]+)\\)$""")
        private val httpStatusInMessage = Regex("""HTTP\\s+(\\d{3})""")

        fun from(error: Throwable, episodeId: String, recentEvents: List<SdkApiTrace>): PlaybackFailure {
            val sdkError = error as? OpenhongguoException
            val sdkMessage = error.message ?: error.toString()
            val sdkCode = sdkError?.code?.name ?: error.javaClass.simpleName
            val businessMatch = businessCode.find(sdkMessage)
            val businessCodeValue = businessMatch?.groupValues?.get(1)?.trim()
            val businessName = businessMatch?.groupValues?.get(2)?.trim()
            val serverMessage = if (businessMatch != null) {
                sdkMessage.substringAfter("openhongguo: ", sdkMessage).substringBeforeLast(" (code ")
            } else {
                null
            }
            val fromException = httpStatusInMessage.find(sdkMessage)?.groupValues?.get(1)?.toIntOrNull()
            val relevantTrace = recentEvents.lastOrNull { it.httpStatus != null && it.endpoint?.contains("/player/", true) == true }
                ?: recentEvents.lastOrNull { it.httpStatus != null }
            val bodyNote = when (sdkError?.code) {
                ErrorCode.ANONYMOUS_LIMITED ->
                    "SDK 检测到响应体为空或只有空白字符；SDK 0.4.0 未向 Android 暴露原始响应字节或字节长度。"
                else ->
                    "SDK 0.4.0 未向 Android 封装层公开完整原始 HTTP 响应体；上方展示的是 SDK 从服务器错误中提取的业务码和错误信息。"
            }
            return PlaybackFailure(
                occurredAt = Instant.now().toString(),
                episodeId = episodeId,
                sdkCode = sdkCode,
                sdkMessage = sdkMessage,
                serverBusinessCode = businessCodeValue,
                serverErrorName = businessName,
                serverErrorMessage = serverMessage,
                httpStatus = fromException ?: relevantTrace?.httpStatus,
                endpoint = relevantTrace?.endpoint,
                responseBodyNote = bodyNote,
                retryable = sdkError?.retryable ?: false,
                retryAfterMs = sdkError?.retryAfterMs ?: 0L,
                events = recentEvents,
            )
        }
    }
}
