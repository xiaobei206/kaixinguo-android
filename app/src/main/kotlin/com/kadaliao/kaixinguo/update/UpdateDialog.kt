package com.kadaliao.kaixinguo.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

sealed interface UpdateUiState {
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val progress: Int) : UpdateUiState
    data class AwaitingPermission(val info: UpdateInfo) : UpdateUiState
    data class Installing(val info: UpdateInfo, val sessionId: Int? = null) : UpdateUiState
    data class AwaitingConfirmation(val info: UpdateInfo, val sessionId: Int, val launchError: String? = null) : UpdateUiState
    data class Failed(val info: UpdateInfo, val message: String, val sessionId: Int? = null) : UpdateUiState
}

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onUpdate: (UpdateInfo) -> Unit,
    onCancel: () -> Unit,
    onRetry: (UpdateInfo) -> Unit,
    onContinueInstall: (Int) -> Unit,
) {
    val info = when (state) {
        is UpdateUiState.Available -> state.info
        is UpdateUiState.Downloading -> state.info
        is UpdateUiState.AwaitingPermission -> state.info
        is UpdateUiState.Installing -> state.info
        is UpdateUiState.AwaitingConfirmation -> state.info
        is UpdateUiState.Failed -> state.info
    }
    val busy = state is UpdateUiState.Downloading ||
        state is UpdateUiState.Installing ||
        state is UpdateUiState.AwaitingConfirmation
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text("红果去广有新版本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("版本 v${info.versionName} 已发布")
                if (info.releaseNotes.isNotBlank()) {
                    Text(
                        info.releaseNotes.take(900),
                        modifier = Modifier.fillMaxWidth().height(180.dp).verticalScroll(rememberScrollState()),
                    )
                }
                when (state) {
                    is UpdateUiState.Available -> Text("将直接在应用内下载并校验安装包。")
                    is UpdateUiState.Downloading -> {
                        LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("正在下载：${state.progress}%")
                    }
                    is UpdateUiState.AwaitingPermission -> Text("首次安装更新需要允许「红果去广」安装未知应用。开启后返回应用即可继续。")
                    is UpdateUiState.Installing -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator()
                        Text("下载与校验完成，正在提交系统安装，等待 Android 返回安装结果。")
                    }
                    is UpdateUiState.AwaitingConfirmation -> {
                        Text("安装包已准备好，系统要求确认安装。点击下方「继续安装」打开系统安装器。")
                        state.launchError?.let { Text("上次打开失败：$it") }
                    }
                    is UpdateUiState.Failed -> Text("更新失败：${state.message}")
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateUiState.Available -> Button(onClick = { onUpdate(info) }) { Text("立即更新") }
                is UpdateUiState.Failed -> Button(onClick = { onRetry(info) }) { Text("重试安装") }
                is UpdateUiState.AwaitingConfirmation -> Button(onClick = { onContinueInstall(state.sessionId) }) { Text("继续安装") }
                is UpdateUiState.AwaitingPermission -> Button(onClick = { onUpdate(info) }) { Text("打开授权设置") }
                else -> {}
            }
        },
        dismissButton = {
            if (!busy) OutlinedButton(onClick = onCancel) { Text("取消") }
        },
    )
}
