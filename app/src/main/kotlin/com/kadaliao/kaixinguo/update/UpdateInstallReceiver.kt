package com.kadaliao.kaixinguo.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        AppUpdater.recordInstallStatus(context, sessionId, status, message)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmation != null) {
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirmation) }
                        .onFailure { error ->
                            Log.e("HongguoQuGuangUpdater", "Could not open system install confirmation", error)
                            AppUpdater.recordInstallStatus(
                                context,
                                sessionId,
                                PackageInstaller.STATUS_FAILURE,
                                "无法打开系统安装确认界面",
                            )
                            Toast.makeText(
                                context,
                                "无法打开系统安装确认界面，请返回应用重试",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                } else {
                    Log.w("HongguoQuGuangUpdater", "Install confirmation status had no Intent")
                    AppUpdater.recordInstallStatus(
                        context,
                        sessionId,
                        PackageInstaller.STATUS_FAILURE,
                        "系统未提供安装确认界面",
                    )
                    Toast.makeText(context, "系统未提供安装确认界面，请重试", Toast.LENGTH_LONG).show()
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i("HongguoQuGuangUpdater", "Package update completed")
                Toast.makeText(context, "红果去广已更新", Toast.LENGTH_LONG).show()
            }
            else -> {
                Log.w(
                    "HongguoQuGuangUpdater",
                    "Package update failed: status=$status message=$message sessionId=$sessionId",
                )
                Toast.makeText(
                    context,
                    if (message.isBlank()) "安装未完成（状态码 $status），可重试安装" else "安装未完成：$message",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.kadaliao.kaixinguo.UPDATE_INSTALL_STATUS"
        const val EXTRA_SESSION_ID = "session_id"
    }
}
