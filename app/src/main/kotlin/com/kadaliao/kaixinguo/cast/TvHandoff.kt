package com.kadaliao.kaixinguo.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class TvDevice(
    val deviceId: String,
    val displayName: String,
    val manufacturer: String,
    val model: String,
    val shortCode: String,
    val serviceName: String,
    val host: String,
    val port: Int,
) {
    val key: String get() = deviceId.ifBlank { serviceName }
    val detail: String
        get() = listOf(
            manufacturer.takeIf { it.isNotBlank() },
            model.takeIf { it.isNotBlank() },
            shortCode.takeIf { it.isNotBlank() }?.let { "编号 $it" },
        ).filterNotNull().joinToString(" · ")
}

data class TvHandoffPayload(
    val seriesId: String,
    val episode: Int,
    val positionMs: Long,
) {
    fun toJson(transferId: String, targetDeviceId: String) = JSONObject()
        .put("protocol", PROTOCOL_VERSION)
        .put("type", "play")
        .put("transferId", transferId)
        .put("targetDeviceId", targetDeviceId)
        .put("seriesId", seriesId)
        .put("episode", episode)
        .put("positionMs", positionMs.coerceAtLeast(0L))

    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("series_id", seriesId)
            .putInt("episode", episode)
            .putLong("position_ms", positionMs.coerceAtLeast(0L))
            .apply()
    }

    companion object {
        const val PROTOCOL_VERSION = 1
        private const val PREFS = "tv_handoff_recent"

        fun load(context: Context): TvHandoffPayload? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val id = prefs.getString("series_id", null)?.takeIf { it.isNotBlank() } ?: return null
            val episode = prefs.getInt("episode", 0)
            if (episode < 1) return null
            return TvHandoffPayload(id, episode, prefs.getLong("position_ms", 0L).coerceAtLeast(0L))
        }
    }
}

data class TvHandoffResponse(val accepted: Boolean, val message: String)

class TvHandoffException(
    message: String,
    val outcomeUnknown: Boolean,
    cause: Throwable? = null,
) : Exception(message, cause)

class TvDiscovery(context: Context) {
    companion object {
        const val SERVICE_TYPE = "_hongguotv._tcp."
        private const val TAG = "KxgTvDiscovery"
    }

    private val manager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val devices = ConcurrentHashMap<String, TvDevice>()
    private val resolving = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var discoveryListener: NsdManager.DiscoveryListener? = null
    @Volatile private var onChanged: ((List<TvDevice>) -> Unit)? = null
    @Volatile private var onError: ((String) -> Unit)? = null

    fun start(changed: (List<TvDevice>) -> Unit, failed: (String) -> Unit) {
        stop()
        devices.clear()
        resolving.clear()
        onChanged = changed
        onError = failed
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.trimEnd('.').equals(SERVICE_TYPE.trimEnd('.'), ignoreCase = true)) return
                val key = serviceInfo.serviceName
                if (!resolving.add(key)) return
                try {
                    manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            resolving.remove(info.serviceName)
                            Log.d(TAG, "Resolve failed: ${info.serviceName} ($errorCode)")
                        }

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            resolving.remove(info.serviceName)
                            val host = info.host?.hostAddress ?: return
                            val deviceId = attribute(info, "deviceid").ifBlank { info.serviceName }
                            val device = TvDevice(
                                deviceId = deviceId,
                                displayName = attribute(info, "label").ifBlank { info.serviceName },
                                manufacturer = attribute(info, "manufacturer"),
                                model = attribute(info, "model"),
                                shortCode = attribute(info, "code"),
                                serviceName = info.serviceName,
                                host = host,
                                port = info.port,
                            )
                            devices[device.key] = device
                            publish()
                        }
                    })
                } catch (e: Exception) {
                    resolving.remove(key)
                    Log.w(TAG, "Unable to resolve ${serviceInfo.serviceName}", e)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                devices.entries.removeIf { it.value.serviceName == serviceInfo.serviceName }
                resolving.remove(serviceInfo.serviceName)
                publish()
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                onError?.invoke("无法搜索电视（错误 $errorCode）")
                runCatching { manager.stopServiceDiscovery(this) }
                discoveryListener = null
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { manager.stopServiceDiscovery(this) }
                discoveryListener = null
            }
        }
        discoveryListener = listener
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            discoveryListener = null
            onError?.invoke(e.message ?: "无法启动局域网搜索")
        }
    }

    fun stop() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        runCatching { manager.stopServiceDiscovery(listener) }
        resolving.clear()
    }

    private fun publish() {
        onChanged?.invoke(devices.values.sortedBy { it.displayName.lowercase() })
    }

    private fun attribute(info: NsdServiceInfo, key: String): String =
        runCatching { info.attributes[key]?.toString(Charsets.UTF_8).orEmpty() }.getOrDefault("").trim()
}

object TvHandoffClient {
    suspend fun send(device: TvDevice, payload: TvHandoffPayload): TvHandoffResponse =
        withContext(Dispatchers.IO) {
            val transferId = UUID.randomUUID().toString()
            val socket = Socket()
            var requestSent = false
            try {
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(device.host, device.port), 4_000)
                socket.soTimeout = 42_000
                val body = payload.toJson(transferId, device.deviceId).toString() + "\n"
                socket.getOutputStream().write(body.toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
                requestSent = true

                val line = socket.getInputStream().bufferedReader(Charsets.UTF_8).readLine()
                    ?: throw TvHandoffException("电视没有返回交接结果", true)
                if (line.length > 8_192) throw TvHandoffException("电视返回的数据过长", true)
                val reply = JSONObject(line)
                if (reply.optString("transferId") != transferId) {
                    throw TvHandoffException("电视返回的交接编号不匹配", true)
                }
                val respondingDevice = reply.optString("deviceId")
                if (respondingDevice.isNotBlank() && respondingDevice != device.deviceId) {
                    throw TvHandoffException("响应来自另一台电视，播放结果未知", true)
                }
                TvHandoffResponse(
                    accepted = reply.optBoolean("accepted", false),
                    message = reply.optString("message").ifBlank {
                        if (reply.optBoolean("accepted")) "电视已开始播放" else "电视无法播放该内容"
                    },
                )
            } catch (e: TvHandoffException) {
                throw e
            } catch (e: SocketTimeoutException) {
                throw TvHandoffException(
                    if (requestSent) "电视可能已经开始播放，但回执超时；请检查电视" else "连接电视超时",
                    outcomeUnknown = requestSent,
                    cause = e,
                )
            } catch (e: Exception) {
                throw TvHandoffException(
                    if (requestSent) "连接中断，电视是否开始播放暂时无法确认" else (e.message ?: "无法连接电视"),
                    outcomeUnknown = requestSent,
                    cause = e,
                )
            } finally {
                runCatching { socket.close() }
            }
        }
}
