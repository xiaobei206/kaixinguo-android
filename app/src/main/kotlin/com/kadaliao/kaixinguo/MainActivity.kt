package com.kadaliao.kaixinguo

import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.util.Rational
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.kadaliao.kaixinguo.model.AppModel
import com.kadaliao.kaixinguo.model.PlayerState
import com.kadaliao.kaixinguo.cast.PhoneDiagnosticReceiver
import com.kadaliao.kaixinguo.cast.PhoneDiagnosticReport
import com.kadaliao.kaixinguo.playback.PlaybackService
import com.kadaliao.kaixinguo.ui.App
import com.kadaliao.kaixinguo.ui.StartFailed
import com.kadaliao.kaixinguo.ui.Tab
import com.kadaliao.kaixinguo.ui.KaixinguoTheme
import com.kadaliao.kaixinguo.ui.Route
import com.kadaliao.kaixinguo.update.AppUpdater
import com.kadaliao.kaixinguo.update.UpdateDialog
import com.kadaliao.kaixinguo.update.UpdateInfo
import com.kadaliao.kaixinguo.update.UpdateInstallReceiver
import com.kadaliao.kaixinguo.update.UpdateUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MainActivity is the app's one window. It opens `kaixinguo://` links,
 * goes into picture in picture when the viewer leaves a playing episode,
 * and pauses the episode when they leave it any other way, to play it on
 * when they come back.
 */
class MainActivity : ComponentActivity() {
    companion object {
        // Survives Activity recreation, but resets after process death so restored tasks still auto-check.
        private var automaticUpdateCheckStarted = false
    }

    private val app get() = application as KaixinguoApplication
    private var inPip by mutableStateOf(false)
    private var updateState by mutableStateOf<UpdateUiState?>(null)
    private var updateCheckInProgress = false
    private var updateJob: Job? = null
    private var pipWanted = false
    private var controller: ListenableFuture<MediaController>? = null
    private var installStateReceiverRegistered = false
    private var diagnosticReceiver: PhoneDiagnosticReceiver? = null
    private val diagnosticPrefs by lazy { getSharedPreferences("diagnostic_receiver", Context.MODE_PRIVATE) }
    private val installStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != AppUpdater.ACTION_INSTALL_STATE_CHANGED) return
            handleInstallStatus(
                sessionId = intent.getIntExtra(UpdateInstallReceiver.EXTRA_SESSION_ID, -1),
                status = intent.getIntExtra(AppUpdater.EXTRA_INSTALL_STATUS, PackageInstaller.STATUS_FAILURE),
                message = intent.getStringExtra(AppUpdater.EXTRA_INSTALL_MESSAGE).orEmpty(),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val model = app.model
        setContent {
            KaixinguoTheme {
                Box(Modifier.fillMaxSize()) {
                    if (model == null) StartFailed(app.startError)
                    else App(model, app.router, app.covers, app.video, inPip)
                    updateState?.let { state ->
                        UpdateDialog(
                            state = state,
                            onUpdate = ::beginUpdate,
                            onCancel = ::cancelUpdate,
                            onRetry = ::beginUpdate,
                        )
                    }
                }
            }
        }
        if (model == null) return
        registerInstallStateReceiver()
        // The launch's first window reopens the player where the viewer
        // left it, unless a link says where to go.
        val link = if (savedInstanceState == null) intent?.data?.takeIf { it.scheme == "kaixinguo" } else null
        model.reopenPlayer(open = link == null)
        link?.let { open(it, model) }
        // Picture in picture follows the episode: on while it plays.
        if (!automaticUpdateCheckStarted) {
            automaticUpdateCheckStarted = true
            checkForUpdate(showFeedback = false)
        }
        lifecycleScope.launch {
            snapshotFlow {
                val p = model.player
                val wanted = p.isPresented && !p.paused && (p.state == PlayerState.Playing || p.state == PlayerState.Buffering)
                wanted to app.video.aspect
            }.collect { (wanted, _) ->
                pipWanted = wanted
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setPip(wanted)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val awaiting = updateState as? UpdateUiState.AwaitingPermission
        if (awaiting != null && AppUpdater.canInstallPackages(this)) {
            startDownloadAndInstall(awaiting.info)
        }
        val sessionId = when (val state = updateState) {
            is UpdateUiState.Installing -> state.sessionId
            is UpdateUiState.AwaitingConfirmation -> state.sessionId
            else -> null
        }
        if (sessionId != null) {
            AppUpdater.readInstallStatus(this, sessionId)?.let {
                handleInstallStatus(sessionId, it.status, it.message)
            }
        }
    }

    override fun onDestroy() {
        if (installStateReceiverRegistered) {
            unregisterReceiver(installStateReceiver)
            installStateReceiverRegistered = false
        }
        super.onDestroy()
    }

    fun isDiagnosticReceiverEnabled(): Boolean =
        diagnosticPrefs.getBoolean("enabled", false)

    fun setDiagnosticReceiverEnabled(enabled: Boolean) {
        diagnosticPrefs.edit().putBoolean("enabled", enabled).apply()
        if (enabled) startDiagnosticReceiverIfEnabled() else stopDiagnosticReceiver()
        Toast.makeText(
            this,
            if (enabled) "已开启局域网诊断接收（仅在开心果前台时有效）" else "已关闭诊断接收",
            Toast.LENGTH_LONG,
        ).show()
    }

    fun diagnosticInboxReports(): List<PhoneDiagnosticReport> =
        PhoneDiagnosticReceiver.readInbox(this)

    private fun startDiagnosticReceiverIfEnabled() {
        if (!isDiagnosticReceiverEnabled() || diagnosticReceiver != null || isFinishing) return
        val receiver = PhoneDiagnosticReceiver(this)
        diagnosticReceiver = receiver
        receiver.start { report ->
            runOnUiThread {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("hongguoTV 播放诊断", report.report))
                Toast.makeText(this, "收到 ${report.sourceName} 诊断信息，已复制到剪贴板", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun stopDiagnosticReceiver() {
        diagnosticReceiver?.stop()
        diagnosticReceiver = null
    }

    private fun registerInstallStateReceiver() {
        if (installStateReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            installStateReceiver,
            IntentFilter(AppUpdater.ACTION_INSTALL_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        installStateReceiverRegistered = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val model = app.model ?: return
        intent.data?.let { open(it, model) }
    }

    override fun onStart() {
        super.onStart()
        startDiagnosticReceiverIfEnabled()
        // Connecting to the session starts its service, which keeps the
        // episode playing with the screen off.
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controller = MediaController.Builder(this, token).buildAsync()
        // Coming back plays on the episode that played as the viewer left.
        app.model?.player?.returnToApp()
    }

    override fun onStop() {
        stopDiagnosticReceiver()
        super.onStop()
        controller?.let { MediaController.releaseFuture(it) }
        controller = null
        val model = app.model ?: return
        // Leaving the app pauses the episode, unless picture in picture
        // shows it, or the screen merely went off.
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive ?: true
        if (!isInPictureInPictureMode && interactive) model.player.leaveApp()
        app.saveSession()
        model.flush() // what was saved last reaches the disk
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Before Android 12 the app starts picture in picture itself.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && pipWanted && canPip()) {
            runCatching { enterPictureInPictureMode(pipParams(true)) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        val player = app.model?.player ?: return
        player.inPictureInPicture = isInPictureInPictureMode
        // Closing the window stops the episode; the app, back, shows what the viewer was browsing.
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) player.leave()
    }

    /**
     * Called from the playback overflow menu. Cold-start checks stay quiet;
     * this explicit check reports the result so the viewer knows it worked.
     */
    fun checkForUpdatesManually() {
        if (updateCheckInProgress) {
            Toast.makeText(this, "正在检查更新，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        if (updateState != null) {
            Toast.makeText(this, "更新窗口已经打开", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "正在检查更新…", Toast.LENGTH_SHORT).show()
        checkForUpdate(showFeedback = true)
    }

    private fun checkForUpdate(showFeedback: Boolean) {
        if (updateCheckInProgress) return
        updateCheckInProgress = true
        lifecycleScope.launch {
            try {
                val info = AppUpdater.check(this@MainActivity)
                if (info != null) {
                    Log.i("HongguoQuGuangUpdater", "Update available: ${info.versionName} (${info.versionCode})")
                    updateState = UpdateUiState.Available(info)
                } else if (showFeedback) {
                    Log.i("HongguoQuGuangUpdater", "Manual update check: already latest")
                    Toast.makeText(this@MainActivity, "当前已经是最新版本", Toast.LENGTH_LONG).show()
                } else {
                    Log.i("HongguoQuGuangUpdater", "Automatic update check completed; no newer release found")
                }
            } catch (e: CancellationException) {
                if (!showFeedback) automaticUpdateCheckStarted = false
                throw e
            } catch (e: Exception) {
                Log.w("HongguoQuGuangUpdater", "Update check failed (manual=$showFeedback)", e)
                val message = e.message?.take(100).orEmpty()
                Toast.makeText(
                    this@MainActivity,
                    if (showFeedback) {
                        if (message.isBlank()) "检查更新失败，请检查网络" else "检查更新失败：$message"
                    } else {
                        if (message.isBlank()) "自动检查更新失败，请检查网络" else "自动检查更新失败：$message"
                    },
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                updateCheckInProgress = false
            }
        }
    }

    private fun handleInstallStatus(sessionId: Int, status: Int, message: String) {
        if (sessionId < 0) return
        val current = updateState ?: return
        val info = when (current) {
            is UpdateUiState.Installing -> {
                if (current.sessionId != null && current.sessionId != sessionId) return
                current.info
            }
            is UpdateUiState.AwaitingConfirmation -> {
                if (current.sessionId != sessionId) return
                current.info
            }
            is UpdateUiState.Failed -> {
                if (current.sessionId != null && current.sessionId != sessionId) return
                current.info
            }
            else -> return
        }

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                updateState = UpdateUiState.AwaitingConfirmation(info, sessionId)
                Log.i("HongguoQuGuangUpdater", "Waiting for system install confirmation; sessionId=$sessionId")
            }
            PackageInstaller.STATUS_SUCCESS -> {
                updateState = null
                Log.i("HongguoQuGuangUpdater", "Package update completed; sessionId=$sessionId")
            }
            else -> {
                val detail = AppUpdater.installFailureMessage(status, message)
                updateState = UpdateUiState.Failed(info, detail, sessionId)
                Log.w("HongguoQuGuangUpdater", "Package update failed; sessionId=$sessionId detail=$detail")
            }
        }
    }

    private fun beginUpdate(info: UpdateInfo) {
        if (!AppUpdater.canInstallPackages(this)) {
            updateState = UpdateUiState.AwaitingPermission(info)
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName"),
            )
            runCatching { startActivity(settingsIntent) }.onFailure {
                updateState = UpdateUiState.Failed(info, "无法打开安装授权设置")
            }
            return
        }
        startDownloadAndInstall(info)
    }

    private fun startDownloadAndInstall(info: UpdateInfo) {
        if (updateJob?.isActive == true) return
        updateState = UpdateUiState.Downloading(info, 0)
        updateJob = lifecycleScope.launch {
            var sessionId: Int? = null
            try {
                // Reuse the APK after a prior install failure when its published digest still matches.
                val cached = AppUpdater.findVerifiedCachedApk(this@MainActivity, info)
                val apk = cached ?: AppUpdater.download(this@MainActivity, info) { progress ->
                    runOnUiThread {
                        if (updateState is UpdateUiState.Downloading) {
                            updateState = UpdateUiState.Downloading(info, progress)
                        }
                    }
                }
                updateState = UpdateUiState.Installing(info)
                // Copying a full APK into PackageInstaller can be slow; keep disk I/O off the UI thread.
                val committedSessionId = withContext(Dispatchers.IO) {
                    AppUpdater.install(this@MainActivity, apk)
                }
                sessionId = committedSessionId
                val current = updateState
                if (current is UpdateUiState.Installing && current.sessionId == null) {
                    updateState = current.copy(sessionId = committedSessionId)
                }
                // The system can report a status before install() returns; reconcile that race.
                AppUpdater.readInstallStatus(this@MainActivity, committedSessionId)?.let {
                    handleInstallStatus(committedSessionId, it.status, it.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("HongguoQuGuangUpdater", "Download or install submission failed", e)
                updateState = UpdateUiState.Failed(info, e.message ?: "未知错误", sessionId)
            }
        }
    }

    private fun cancelUpdate() {
        updateJob?.cancel()
        updateJob = null
        updateState = null
    }

    private fun canPip() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun setPip(wanted: Boolean) {
        if (!canPip()) return
        runCatching { setPictureInPictureParams(pipParams(wanted)) }
    }

    private fun pipParams(autoEnter: Boolean): PictureInPictureParams {
        // The system takes ratios between 1:2.39 and 2.39:1.
        val ratio = app.video.aspect.coerceIn(1 / 2.39f + 0.001f, 2.39f - 0.001f)
        val b = PictureInPictureParams.Builder().setAspectRatio(Rational((ratio * 10000).toInt(), 10000))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            b.setAutoEnterEnabled(autoEnter).setSeamlessResizeEnabled(false)
        }
        return b.build()
    }

    /**
     * Opens a link: kaixinguo://series/<id>, kaixinguo://play/<id>?episode=<n>,
     * kaixinguo://search?q=<words>, or a tab, as kaixinguo://rank.
     */
    private fun open(uri: Uri, model: AppModel) {
        if (uri.scheme != "kaixinguo") return
        val router = app.router
        val id = uri.pathSegments.firstOrNull()
        if (uri.host == "play") {
            if (id == null) return
            val n = uri.getQueryParameter("episode")?.toIntOrNull()
            if (n != null) model.player.play(id, n) else model.playSeason(id)
            return
        }
        // The other links show a page, which the player would cover.
        if (model.player.isPresented) model.player.leave()
        when (uri.host) {
            "series" -> if (id != null) router.open(Route.Series(id))
            "discover" -> router.select(Tab.DISCOVER)
            "rank" -> router.select(Tab.RANK)
            "new" -> router.select(Tab.NEW)
            "mine" -> router.select(Tab.MINE)
            "search" -> {
                router.select(Tab.SEARCH)
                uri.getQueryParameter("q")?.let { router.search(it) }
            }
        }
    }
}
