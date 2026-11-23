package com.kadaliao.kaixinguo.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class PhoneDiagnosticReport(
    val reportId: String,
    val sourceName: String,
    val sourceVersion: String,
    val receivedAt: String,
    val report: String,
)

class PhoneDiagnosticReceiver(context: Context) {
    companion object {
        const val SERVICE_TYPE = "_kaixinguo-diagnostic._tcp."
        private const val TAG = "KxgDiagnosticRx"
        private const val PROTOCOL_VERSION = 1
        private const val MAX_REQUEST_BYTES = 256 * 1024
        private const val MAX_REPORT_CHARS = 220 * 1024
        private const val MAX_INBOX_ITEMS = 30

        fun readInbox(context: Context): List<PhoneDiagnosticReport> {
            val folder = File(context.applicationContext.filesDir, "diagnostic-inbox")
            return folder.listFiles { file -> file.isFile && file.extension == "json" }
                .orEmpty()
                .mapNotNull { file ->
                    runCatching {
                        val json = JSONObject(file.readText(Charsets.UTF_8))
                        PhoneDiagnosticReport(
                            reportId = json.getString("reportId"),
                            sourceName = json.optString("sourceName", "hongguoTV"),
                            sourceVersion = json.optString("sourceVersion"),
                            receivedAt = json.optString("receivedAt"),
                            report = json.getString("report"),
                        )
                    }.getOrNull()
                }
                .sortedByDescending { it.receivedAt }
        }
    }

    private val app = context.applicationContext
    private val nsd = app.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val executor = Executors.newCachedThreadPool { task ->
        Thread(task, "kaixinguo-diagnostic-rx").apply { isDaemon = true }
    }
    private val started = AtomicBoolean(false)
    @Volatile private var server: ServerSocket? = null
    @Volatile private var registration: NsdManager.RegistrationListener? = null
    @Volatile private var onReceived: ((PhoneDiagnosticReport) -> Unit)? = null

    fun start(received: (PhoneDiagnosticReport) -> Unit) {
        if (!started.compareAndSet(false, true)) return
        onReceived = received
        executor.execute {
            try {
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(0))
                server = socket
                registerService(socket.localPort)
                while (!socket.isClosed) {
                    val client = socket.accept()
                    executor.execute { handleClient(client) }
                }
            } catch (e: Exception) {
                if (started.get()) Log.e(TAG, "diagnostic receiver stopped", e)
            }
        }
    }

    fun stop() {
        started.set(false)
        registration?.let { listener -> runCatching { nsd.unregisterService(listener) } }
        registration = null
        runCatching { server?.close() }
        server = null
        onReceived = null
        executor.shutdownNow()
    }

    private fun registerService(port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = "kaixinguo-diag-" + UUID.randomUUID().toString().take(8)
            serviceType = SERVICE_TYPE
            this.port = port
            setAttribute("label", "开心果")
            setAttribute("protocol", PROTOCOL_VERSION.toString())
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "diagnostic receiver advertised: ${serviceInfo.serviceName}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD registration failed: $errorCode")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "diagnostic receiver stopped")
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD unregister failed: $errorCode")
            }
        }
        registration = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { Log.e(TAG, "unable to advertise diagnostics", it) }
    }

    private fun handleClient(socket: java.net.Socket) {
        socket.use { client ->
            try {
                client.soTimeout = 8_000
                val text = readLineLimited(client) ?: run {
                    writeReply(client, "", false, "诊断请求为空")
                    return
                }
                val json = JSONObject(text)
                val reportId = json.optString("reportId")
                val report = json.optString("report")
                if (json.optInt("protocol") != PROTOCOL_VERSION ||
                    json.optString("type") != "diagnostic" ||
                    reportId.isBlank() || reportId.length > 80 ||
                    report.isBlank() || report.length > MAX_REPORT_CHARS
                ) {
                    writeReply(client, reportId, false, "诊断格式无效或超出大小限制")
                    return
                }
                val item = PhoneDiagnosticReport(
                    reportId = reportId,
                    sourceName = json.optString("sourceName", "hongguoTV").take(60),
                    sourceVersion = json.optString("sourceVersion").take(40),
                    receivedAt = Instant.now().toString(),
                    report = report,
                )
                if (!persist(item)) {
                    writeReply(client, reportId, false, "这份诊断已经收到")
                    return
                }
                writeReply(client, reportId, true, "诊断已保存")
                onReceived?.invoke(item)
            } catch (e: Exception) {
                Log.w(TAG, "diagnostic transfer rejected", e)
                runCatching { writeReply(client, "", false, "接收诊断失败") }
            }
        }
    }

    private fun persist(item: PhoneDiagnosticReport): Boolean {
        val folder = File(app.filesDir, "diagnostic-inbox")
        if (!folder.exists() && !folder.mkdirs()) return false
        val safeId = item.reportId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)
        val target = File(folder, "$safeId.json")
        if (target.exists()) return false
        val json = JSONObject()
            .put("reportId", item.reportId)
            .put("sourceName", item.sourceName)
            .put("sourceVersion", item.sourceVersion)
            .put("receivedAt", item.receivedAt)
            .put("report", item.report)
        val temporary = File(folder, "$safeId.tmp")
        temporary.writeText(json.toString(), Charsets.UTF_8)
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        folder.listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .drop(MAX_INBOX_ITEMS)
            .forEach { it.delete() }
        return target.exists()
    }

    private fun readLineLimited(socket: java.net.Socket): String? {
        val output = ByteArrayOutputStream()
        val input = socket.getInputStream()
        while (output.size() <= MAX_REQUEST_BYTES) {
            val value = input.read()
            if (value < 0) break
            if (value == 10) return output.toByteArray().toString(Charsets.UTF_8).trimEnd('\r')
            output.write(value)
        }
        if (output.size() > MAX_REQUEST_BYTES) throw IllegalArgumentException("diagnostic request too large")
        return output.toByteArray().toString(Charsets.UTF_8).takeIf { it.isNotBlank() }
    }

    private fun writeReply(socket: java.net.Socket, reportId: String, accepted: Boolean, message: String) {
        val json = JSONObject()
            .put("protocol", PROTOCOL_VERSION)
            .put("type", "diagnostic_ack")
            .put("reportId", reportId)
            .put("accepted", accepted)
            .put("message", message)
        socket.getOutputStream().write((json.toString() + "\n").toByteArray(Charsets.UTF_8))
        socket.getOutputStream().flush()
    }
}
