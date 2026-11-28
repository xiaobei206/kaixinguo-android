package com.kadaliao.kaixinguo.ui

import android.content.pm.PackageInfo
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The one-off birthday easter egg and the reusable app-information dialog. */
@Composable
fun BirthdaySpecialDialog(
    onDismiss: () -> Unit,
    welcome: Boolean = false,
) {
    val context = LocalContext.current
    val packageInfo: PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    }.getOrNull()
    val versionName = packageInfo?.versionName ?: "未知"
    val versionCode = packageInfo?.let {
        if (Build.VERSION.SDK_INT >= 28) it.longVersionCode.toString() else it.versionCode.toString()
    } ?: "未知"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF242019), Color(0xFF111111))))
                    .padding(horizontal = 18.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("✦", color = Color(0xFFE2C27A), style = MaterialTheme.typography.headlineMedium)
                Text(
                    "丅芈孒 生日特别版",
                    color = Color(0xFFE2C27A),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text("2026年11月28日", color = Color(0xFFC9B78D), style = MaterialTheme.typography.labelMedium)
                Text(
                    "愿快乐常伴，好剧常在。",
                    color = Color(0xFFF4EBDD),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Text("A Little Gift for 丅芈孒", color = Color(0xFFB5B0A5), style = MaterialTheme.typography.labelSmall)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("应用名称", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("开心果", fontWeight = FontWeight.Medium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("版本号", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("$versionName ($versionCode)", fontWeight = FontWeight.Medium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("开发者", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("by Edison", fontWeight = FontWeight.Medium)
                }
                if (welcome) {
                    Text(
                        "生日快乐！这份小彩蛋已经收好，之后也可以在“我的”右上角菜单里再次打开。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (welcome) "开始使用" else "关闭") }
        },
    )
}
