package com.kadaliao.kaixinguo.model

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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

/** Redacts data that should never be copied into a user-shared diagnostic report. */
internal fun sanitizeDiagnosticText(value: String): String {
    var safe = value
    safe = safe.replace(Regex("""(?i)(https?://[^\s"'<>?]+)\?[^\s"'<>]+""")) {
        it.groupValues[1] + "?<query-redacted>"
    }
    safe = safe.replace(
        Regex("""(?i)\b(authorization|proxy-authorization|cookie|set-cookie|access_token|refresh_token|token|device[_-]?id|anonymous[_-]?id|install[_-]?id|openudid|clientudid|cdid|klink[_-]?egdi|egdi|signature|secret|password|api[_-]?key)\b(\s*[:=]\s*)(?:(?:Bearer|Basic)\s+)?("[^"]*"|'[^']*'|[^,\s;]+)(?:\s+[^,\s;]+)?"""),
    ) {
        it.groupValues[1] + it.groupValues[2] + "<redacted>"
    }
    safe = safe.replace(
        Regex("""(?i)([?&](?:token|access_token|refresh_token|device_id|anonymous_id|install_id|signature|secret|password|api_key)=)[^&#\s]+"""),
    ) {
        it.groupValues[1] + "<redacted>"
    }
    return safe.take(1200)
}

object SdkDiagnostics {
    private const val MAX_EVENTS = 128
    private val lock = Any()
    private val events = ArrayDeque<SdkApiTrace>()
    @Volatile private var deviceEnvironment = "设备环境尚未初始化"
    @Volatile private var appContext: Context? = null
    private val httpLine = Regex("""^HTTP\s+(\S+)\s+(https?://\S+)\s+->\s+(\d+)\s*$""")

    fun initialize(context: Context) {
        val app = context.applicationContext
        appContext = app
        deviceEnvironment = runCatching {
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

    fun appVersionSummary(): String {
        val app = appContext ?: return "应用上下文尚未初始化"
        return runCatching {
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            "包名=${app.packageName}；版本=${info.versionName ?: "未知"}；versionCode=${info.longVersionCode}"
        }.getOrElse { "应用版本读取失败：${it.javaClass.simpleName}" }
    }

    fun networkSummary(): String {
        val app = appContext ?: return "网络状态未知：应用上下文尚未初始化"
        return runCatching {
            val manager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = manager?.activeNetwork
            val caps = if (manager != null && network != null) manager.getNetworkCapabilities(network) else null
            if (caps == null) return@runCatching "系统未报告活动网络"
            val transports = buildList {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("Wi-Fi")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("移动网络")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("以太网")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("蓝牙网络")
            }.ifEmpty { listOf("其他/未知传输") }.joinToString("/")
            "${transports}；具备互联网能力=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}；系统已验证可联网=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}；按流量计费=${!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)}"
        }.getOrElse { "网络状态读取失败：${it.javaClass.simpleName}" }
    }

    fun record(line: String) {
        val rawLine = line.trim().take(1200)
        val match = httpLine.matchEntire(rawLine)
        val rawUrl = match?.groupValues?.get(2)
        val safeLine = if (match != null && rawUrl != null) {
            "HTTP ${match.groupValues[1]} ${rawUrl.substringBefore('?').substringBefore('#')} -> ${match.groupValues[3]}"
        } else {
            sanitizeDiagnosticText(rawLine).take(600)
        }
        val isDeviceRotation = safeLine.contains("anonymous device unusable", ignoreCase = true) ||
            safeLine.contains("registering a new anonymous device", ignoreCase = true)
        val isProfileEvent = safeLine.contains("anonymous profile", ignoreCase = true) ||
            safeLine.contains("anonymous registration", ignoreCase = true)
        val isPlaybackEvent = safeLine.startsWith("playback ", ignoreCase = true)
        if (match == null && !isDeviceRotation && !isProfileEvent && !isPlaybackEvent) return

        val url = rawUrl?.substringBefore('?')?.substringBefore('#')
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


/**
 * A copy-ready, local-only report for a failed catalogue/feed request.
 * It keeps diagnostic metadata and call stacks, not raw request bodies or credentials.
 */
fun Feed.toDebugReport(failure: Throwable): String = buildString {
    val events = SdkDiagnostics.snapshot()
    val sdkError = generateSequence(failure) { it.cause }
        .filterIsInstance<OpenhongguoException>()
        .firstOrNull()
    fun sdkField(name: String): String {
        if (sdkError == null) return "非 OpenhongguoException / SDK 未提供"
        val getter = "get" + name.replaceFirstChar { it.uppercase() }
        val value = runCatching {
            sdkError.javaClass.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }
                ?.invoke(sdkError)
        }.getOrNull() ?: return "SDK 未提供"
        return sanitizeDiagnosticText(value.toString()).ifBlank { "空值" }
    }

    appendLine("开心果 Android 列表故障 DEBUG 诊断")
    appendLine("报告生成时间：${Instant.now()}")
    appendLine("发生页面：${sanitizeDiagnosticText(diagnosticName)}")
    appendLine("请求实现/筛选上下文：${sanitizeDiagnosticText(diagnosticContext.ifBlank { "未标记" })}")
    appendLine("应用版本：${sanitizeDiagnosticText(SdkDiagnostics.appVersionSummary())}")
    appendLine("Android/设备环境：${sanitizeDiagnosticText(SdkDiagnostics.environmentSummary())}")
    appendLine("当前网络状态：${SdkDiagnostics.networkSummary()}")
    appendLine()

    appendLine("===== 列表状态 =====")
    appendLine("已启动：${started}")
    appendLine("当前正在请求：${loading}")
    appendLine("正在自动重试：${retrying}")
    appendLine("是否使用过期缓存等待刷新：${stale}")
    appendLine("当前卡片数：${cards.size}")
    appendLine("本地缓存首次载入数：${cacheCardCount}")
    appendLine("是否还有下一页：${more}")
    appendLine("当前游标是否存在：${currentCursorPresent}（长度=${currentCursorLength}；不复制原值）")
    appendLine("最近一次请求的游标是否存在：${lastRequestedCursorPresent}（长度=${lastRequestedCursorLength}；不复制原值）")
    appendLine("请求总次数：${requestAttempts}")
    appendLine("成功请求次数：${successfulRequests}")
    appendLine("已安排自动重试次数：${automaticRetryCount}")
    appendLine("最近请求时间：${lastRequestAt ?: "无"}")
    appendLine("最近成功时间：${lastSuccessAt ?: "无"}")
    appendLine("最近失败时间：${lastFailureAt ?: "无"}")
    appendLine()

    appendLine("===== 当前列表自身的最近请求历史（最多 40 条） =====")
    val feedHistory = diagnosticHistory()
    if (feedHistory.isEmpty()) appendLine("（没有列表请求历史记录）")
    feedHistory.forEach { appendLine(it) }
    appendLine()

    appendLine("===== SDK / 服务器结构化错误字段 =====")
    appendLine("异常类型：${failure.javaClass.name}")
    appendLine("SDK 错误码：${sdkError?.code?.name ?: "未暴露为 OpenhongguoException"}")
    appendLine("SDK 错误信息：${sanitizeDiagnosticText(failure.message ?: failure.toString())}")
    listOf(
        "serverCode" to "服务器业务码",
        "serverName" to "服务器错误名称",
        "serverMessage" to "服务器原始错误信息（脱敏）",
        "httpStatus" to "异常附带 HTTP 状态码",
        "requestMethod" to "异常附带请求方法",
        "requestPath" to "异常附带请求路径",
        "responseBytes" to "响应传输字节数",
        "decodedResponseBytes" to "解压后响应字节数",
        "responseContentType" to "响应 Content-Type",
        "responseContentEncoding" to "响应 Content-Encoding",
        "retryable" to "SDK 是否建议重试",
        "retryAfterMs" to "SDK 建议重试等待毫秒数",
    ).forEach { (field, label) ->
        appendLine("${label}：${sdkField(field)}")
    }
    appendLine()

    appendLine("===== 异常原因链（最多 8 层） =====")
    var current: Throwable? = failure
    var depth = 0
    val seen = HashSet<Int>()
    while (current != null && depth < 8 && seen.add(System.identityHashCode(current))) {
        appendLine("[${depth}] ${current.javaClass.name}: ${sanitizeDiagnosticText(current.message ?: "无 message")}")
        current = current.cause
        depth++
    }
    appendLine()

    appendLine("===== 异常调用栈（每层最多 24 帧） =====")
    current = failure
    depth = 0
    val stackSeen = HashSet<Int>()
    while (current != null && depth < 8 && stackSeen.add(System.identityHashCode(current))) {
        appendLine("-- cause[${depth}] ${current.javaClass.name} --")
        current.stackTrace.take(24).forEach { frame -> appendLine("  at ${frame}") }
        current = current.cause
        depth++
    }
    appendLine()

    appendLine("===== 最近 SDK 网络/设备事件（最多保留 100 条） =====")
    appendLine("内存事件总数：${events.size}；以下是最近 ${events.takeLast(100).size} 条，时间为 UTC ISO-8601")
    if (events.isEmpty()) appendLine("（没有捕获到匹配的 SDK 请求日志；可结合异常字段判断请求是否到达 SDK 网络层）")
    events.takeLast(100).forEach { event ->
        appendLine("${event.time} | ${event.message}")
        if (event.httpStatus != null) {
            appendLine("    method=${event.method ?: "未知"}; endpoint=${event.endpoint ?: "未知"}; HTTP=${event.httpStatus}")
        }
    }
    appendLine()
    appendLine("隐私处理：URL 查询参数整体移除；常见 Cookie/Token/签名/设备标识字段脱敏；不包含原始游标、Cookie、Token、完整请求或响应正文。")
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
