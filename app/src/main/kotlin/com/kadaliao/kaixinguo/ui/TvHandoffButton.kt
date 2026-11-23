package com.kadaliao.kaixinguo.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kadaliao.kaixinguo.KaixinguoApplication
import com.kadaliao.kaixinguo.cast.TvDevice
import com.kadaliao.kaixinguo.cast.TvDiscovery
import com.kadaliao.kaixinguo.cast.TvHandoffClient
import com.kadaliao.kaixinguo.cast.TvHandoffException
import com.kadaliao.kaixinguo.cast.TvHandoffPayload
import com.kadaliao.kaixinguo.model.AppModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TvHandoffButton(model: AppModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val application = context.applicationContext as KaixinguoApplication
    val scope = rememberCoroutineScope()
    val discovery = remember(context) { TvDiscovery(context) }
    var showDialog by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(emptyList<TvDevice>()) }
    var error by remember { mutableStateOf("") }
    var recent by remember { mutableStateOf(TvHandoffPayload.load(context)) }

    DisposableEffect(discovery) {
        onDispose { discovery.stop() }
    }

    LaunchedEffect(showDialog, scanning) {
        if (showDialog && scanning) {
            delay(7_000)
            scanning = false
            if (devices.isEmpty()) {
                error = "暂时没有发现电视。请确认 hongguoTV 已打开，并检查手机和电视是否连接同一个 Wi-Fi。"
            }
        }
    }

    LaunchedEffect(model) {
        while (true) {
            val player = model.player
            val id = player.seriesID
            if (player.isPresented && !id.isNullOrBlank() && player.number > 0) {
                val raw = player.video.position
                val fallback = model.history.get(id)?.takeIf { it.episode == player.number && !it.finished }?.position ?: 0.0
                val seconds = if (raw.isFinite() && raw >= 0.0) raw else fallback
                val snapshot = TvHandoffPayload(id, player.number, (seconds * 1000.0).toLong().coerceAtLeast(0L))
                recent = snapshot
                snapshot.save(context)
            }
            delay(2_000)
        }
    }

    fun search() {
        discovery.stop()
        devices = emptyList()
        error = ""
        scanning = true
        discovery.start(
            changed = { devices = it },
            failed = { error = it; scanning = false },
        )
    }

    fun currentPayload(): TvHandoffPayload? {
        val player = model.player
        val id = player.seriesID
        if (player.isPresented && !id.isNullOrBlank() && player.number > 0) {
            val raw = player.video.position
            val fallback = model.history.get(id)?.takeIf { it.episode == player.number && !it.finished }?.position ?: 0.0
            val seconds = if (raw.isFinite() && raw >= 0.0) raw else fallback
            return TvHandoffPayload(id, player.number, (seconds * 1000.0).toLong().coerceAtLeast(0L))
        }
        return recent ?: TvHandoffPayload.load(context)
    }

    IconButton(
        onClick = {
            showDialog = true
            search()
        },
        enabled = !sending,
        modifier = modifier,
    ) {
        Icon(Icons.Filled.Cast, contentDescription = "交给电视", tint = MaterialTheme.colorScheme.onSurface)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!sending) {
                    showDialog = false
                    scanning = false
                    discovery.stop()
                }
            },
            title = { Text("选择电视") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "搜索正在运行的 hongguoTV。选择电视后将交接剧集和播放进度。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (sending) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator()
                            Spacer(Modifier.width(12.dp))
                            Text("正在交给电视播放…")
                        }
                    } else if (scanning && devices.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator()
                            Spacer(Modifier.width(12.dp))
                            Text("正在搜索局域网电视…")
                        }
                    } else if (devices.isEmpty()) {
                        Text(error.ifBlank { "没有发现电视。请先在电视上打开 hongguoTV，再重新搜索。" })
                    }
                    if (devices.isNotEmpty()) {
                        LazyColumn(modifier = Modifier.height(260.dp)) {
                            items(devices, key = { it.key }) { device ->
                                TextButton(
                                    onClick = {
                                        val payload = currentPayload()
                                        if (payload == null) {
                                            Toast.makeText(context, "请先播放一集，再交给电视", Toast.LENGTH_LONG).show()
                                        } else {
                                            sending = true
                                            scanning = false
                                            discovery.stop()
                                            payload.save(context)
                                            recent = payload
                                            val player = model.player
                                            val active = player.isPresented && player.seriesID == payload.seriesId &&
                                                player.number == payload.episode
                                            val resumeOnFailure = active && player.playing
                                            if (active) player.video.pause()
                                            scope.launch {
                                                try {
                                                    val response = TvHandoffClient.send(device, payload)
                                                    if (response.accepted) {
                                                        TvHandoffResultRecord.save(
                                                            context, device.displayName, payload,
                                                            success = true, outcomeUnknown = false,
                                                            message = response.message.ifBlank { "电视已确认开始播放" },
                                                        )
                                                        if (active) {
                                                            player.leave()
                                                            application.saveSession()
                                                        }
                                                        val title = model.history.get(payload.seriesId)?.title.orEmpty()
                                                        val item = title.ifBlank { "第 ${payload.episode} 集" }
                                                        Toast.makeText(
                                                            context,
                                                            "投屏成功：${device.displayName} 已确认开始播放（$item）",
                                                            Toast.LENGTH_LONG,
                                                        ).show()
                                                        showDialog = false
                                                    } else {
                                                        TvHandoffResultRecord.save(
                                                            context, device.displayName, payload,
                                                            success = false, outcomeUnknown = false,
                                                            message = response.message,
                                                        )
                                                        if (resumeOnFailure) player.video.play()
                                                        error = response.message
                                                    }
                                                } catch (e: TvHandoffException) {
                                                    val message = e.message ?: "交接失败"
                                                    TvHandoffResultRecord.save(
                                                        context, device.displayName, payload,
                                                        success = false, outcomeUnknown = e.outcomeUnknown,
                                                        message = message,
                                                    )
                                                    if (!e.outcomeUnknown && resumeOnFailure) player.video.play()
                                                    error = message
                                                } catch (e: Exception) {
                                                    val message = e.message ?: "交接失败"
                                                    TvHandoffResultRecord.save(
                                                        context, device.displayName, payload,
                                                        success = false, outcomeUnknown = false,
                                                        message = message,
                                                    )
                                                    if (resumeOnFailure) player.video.play()
                                                    error = message
                                                } finally {
                                                    sending = false
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Icon(Icons.Filled.Tv, contentDescription = null)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(device.displayName, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                device.detail.ifBlank { device.serviceName },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                    if (error.isNotBlank() && devices.isNotEmpty() && !sending) {
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { search() }, enabled = !sending) { Text("重新搜索") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!sending) {
                            showDialog = false
                            scanning = false
                            discovery.stop()
                        }
                    },
                    enabled = !sending,
                ) { Text("关闭") }
            },
        )
    }
}
