@file:OptIn(ExperimentalMaterial3Api::class)

package com.kadaliao.kaixinguo.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast as AndroidToast
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kadaliao.kaixinguo.model.Card
import com.kadaliao.kaixinguo.model.DoubleSpeed
import com.kadaliao.kaixinguo.model.Fav
import com.kadaliao.kaixinguo.model.Lanes
import com.kadaliao.kaixinguo.model.Player
import com.kadaliao.kaixinguo.model.PlayerState
import com.kadaliao.kaixinguo.model.Sleep
import com.kadaliao.kaixinguo.model.favLabel
import com.kadaliao.kaixinguo.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * PlayerScreen is the episode playing, its video filling the screen: what
 * plays and how fast above it, the episodes below it on phones and, when
 * requested, beside it on tablets. Swiping up goes on to the next episode, and down back to
 * the one before; upper-half horizontal swipes set brightness, while
 * lower-half horizontal swipes seek backward or forward. In picture in picture
 * only the video shows.
 */
@Composable
fun PlayerScreen(inPip: Boolean) {
    val model = LocalModel.current
    val player = model.player
    val video = player.video
    val config = LocalConfiguration.current
    val tablet = config.smallestScreenWidthDp >= 600
    val landscape = !tablet && config.orientation == Configuration.ORIENTATION_LANDSCAPE && config.screenHeightDp < 500 // a phone on its side: the video alone
    val panel = tablet
    var showTabletEpisodes by rememberSaveable { mutableStateOf(false) }
    var showEpisodes by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var resumeAfterSearch by remember { mutableStateOf(false) }
    val hud = remember { PlayerHud() }
    val rotation = rememberRotation()
    // The little window shows the video as it is, and the dimming doesn't come back with the screen.
    LaunchedEffect(inPip) { if (inPip) hud.brightness = 1f }
    // A new episode starts with the controls available; playback will hide them after a short idle period.
    LaunchedEffect(player.number) {
        hud.controlsVisible = true
        hud.controlsPinned = false
        if (panel) showTabletEpisodes = false
    }

    // Pauses the episode for a search, to go on with it after.
    fun openSearch() {
        resumeAfterSearch = video.wantsToPlay
        video.pause()
        searching = true
    }

    // Leaves the search: to play the series picked, or the episode as it was.
    fun closeSearch(picked: Card?) {
        searching = false
        if (picked != null) model.playSeason(picked.id) else if (resumeAfterSearch) video.play()
    }

    BackHandler(enabled = !inPip) { if (searching) closeSearch(null) else player.leave() }
    PlayerWindow(
        hideBars = landscape || inPip || (panel && !showTabletEpisodes),
        keepOn = !player.paused && (player.state == PlayerState.Playing || player.state == PlayerState.Buffering),
    )
    PlayerTheme {
        val keys = remember { FocusRequester() }
        LaunchedEffect(searching) { if (!searching) runCatching { keys.requestFocus() } }
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .focusRequester(keys).focusable()
                .onPreviewKeyEvent { e -> !searching && e.type == KeyEventType.KeyDown && playerKey(player, e.key) },
        ) {
            // One layout for all, so the video keeps its place, and its
            // surface, as the device turns or picture in picture starts.
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    VideoArea(hud, edge = landscape || panel, chrome = !inPip, rotation = rotation, landscape = landscape, search = ::openSearch, tabletEpisodesVisible = showTabletEpisodes, toggleTabletEpisodes = if (panel) ({ showTabletEpisodes = !showTabletEpisodes }) else null, modifier = Modifier.weight(1f).fillMaxWidth())
                    if (!inPip && !landscape && !panel) EpisodeBar { showEpisodes = true }
                }
                if (!inPip && panel && showTabletEpisodes) {
                    EpisodePanel(
                        Modifier.width(320.dp).fillMaxHeight().background(Color(0xFF171717))
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)),
                        steps = true,
                        picked = { showTabletEpisodes = false },
                    )
                }
            }
            AnimatedVisibility(
                visible = searching && !inPip,
                enter = slideInHorizontally(tween(250)) { it },
                exit = slideOutHorizontally(tween(250)) { it },
            ) {
                PlayerSearch(::closeSearch)
            }
        }
        if (showEpisodes && !inPip) {
            ModalBottomSheet(onDismissRequest = { showEpisodes = false }, containerColor = Color(0xFF171717)) {
                EpisodePanel(Modifier.fillMaxHeight(0.85f)) { showEpisodes = false }
            }
        }
    }
}

/** The keys of a keyboard that work the video: space, the arrows and Esc. */
private fun playerKey(player: Player, key: Key): Boolean {
    when (key) {
        Key.Spacebar -> player.togglePause()
        Key.DirectionLeft -> player.skip(-10.0)
        Key.DirectionRight -> player.skip(10.0)
        Key.DirectionUp -> player.step(-1)
        Key.DirectionDown -> player.step(1)
        Key.Escape -> player.leave()
        else -> return false
    }
    return true
}

/** PlayerWindow darkens the system bars around the video, hides them when it fills the screen, and keeps the screen on while it plays. */
@Composable
private fun PlayerWindow(hideBars: Boolean, keepOn: Boolean) {
    val view = LocalView.current
    val window = LocalActivity.current?.window ?: return
    val controller = remember(window, view) { WindowCompat.getInsetsController(window, view) }
    DisposableEffect(controller) {
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatus
            controller.isAppearanceLightNavigationBars = lightNav
        }
    }
    DisposableEffect(hideBars) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hideBars) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * The player screen's [Rotation], which hears the phone turn while a press
 * holds the screen, and lets go as the player closes.
 */
@Composable
private fun rememberRotation(): Rotation? {
    val activity = LocalActivity.current ?: return null
    val rotation = remember(activity) { Rotation(activity) }
    val holding = rotation.held != null
    DisposableEffect(rotation, holding) {
        val listener = object : OrientationEventListener(activity) {
            override fun onOrientationChanged(degrees: Int) = rotation.phoneTurned(degrees)
        }
        if (holding) listener.enable()
        onDispose { listener.disable() }
    }
    DisposableEffect(rotation) { onDispose { rotation.release() } }
    return rotation
}

/**
 * Rotation turns the screen at a press: on its side, and upright again.
 * The way pressed holds until the phone is turned that way too, when the
 * screen goes back to following the phone, if auto-rotate is on; and as
 * the player closes.
 */
private class Rotation(private val activity: Activity) {
    /** The way a press turned the screen, while it holds: true on its side, false upright. */
    var held by mutableStateOf<Boolean?>(null)
        private set

    fun turn(landscape: Boolean) {
        held = landscape
        activity.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    /** Lets the screen follow the phone again, and the auto-rotate setting. */
    fun release() {
        if (held == null) return
        held = null
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    /**
     * Hears the phone turn, in degrees from upright. With auto-rotate off
     * the screen wouldn't follow the phone, so the press holds.
     */
    fun phoneTurned(degrees: Int) {
        val held = held ?: return
        if (degrees == OrientationEventListener.ORIENTATION_UNKNOWN) return // lying flat
        val side = degrees in 60..120 || degrees in 240..300
        val upright = degrees <= 30 || degrees >= 330
        if ((if (held) side else upright) && autoRotate()) release()
    }

    private fun autoRotate() =
        Settings.System.getInt(activity.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
}

/**
 * PlayerHud is what the fingers on the video are doing, for what shows over
 * it: a press and hold as it goes, the video following a swipe, and a word
 * on what the last one did.
 */
private class PlayerHud {
    /** Whether the top controls and bottom seek bar are on screen. */
    var controlsVisible by mutableStateOf(true)

    /** Temporarily prevents auto-hide while a gesture, menu, or seek drag is active. */
    var controlsPinned by mutableStateOf(false)

    /** Whether the YouTube-style playback diagnostics overlay is visible. */
    var diagnosticsVisible by mutableStateOf(false)

    var holding by mutableStateOf(false)

    /** Whether the held finger has swiped far enough that letting go flips the lock. */
    var swiped by mutableStateOf(false)

    /** How far the video has followed a swipe up or down, in pixels. */
    var offset by mutableFloatStateOf(0f)

    /**
     * How bright the video shows, from 0 to 1: below 1 a dark layer covers
     * it, so the system's brightness, and its permission, are left alone.
     * It lasts while the player is open, upright or on its side.
     */
    var brightness by mutableFloatStateOf(1f)

    /** A lower-half horizontal scrub is in progress. */
    var seeking by mutableStateOf(false)

    /** Where the scrub began, where it currently points, and the episode length, in seconds. */
    var seekPreviewStart by mutableDoubleStateOf(0.0)
    var seekPreviewPosition by mutableDoubleStateOf(0.0)
    var seekPreviewDuration by mutableDoubleStateOf(0.0)

    /** A small captured video frame shown while scrubbing. */
    var seekPreviewBitmap by mutableStateOf<Bitmap?>(null)
    var seekPreviewRequestId by mutableLongStateOf(0L)

    /** PlayerView's SurfaceView lets PixelCopy capture the rendered video frame at the target time. */
    var surfaceView: SurfaceView? = null

    /** What the last gesture did, or couldn't, said for a moment. */
    var toast by mutableStateOf<Toast?>(null)

    fun say(text: String) {
        toast = Toast(text)
    }
}

/** A word for a moment; each one shows anew, the same words or not. */
private class Toast(val text: String)

private val shadowed = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.4f), blurRadius = 6f))

/**
 * VideoArea is the video and what lies over it: what plays and how fast at
 * the top, how far it has got at the bottom, and what a press, a pause or a
 * failure shows. The video goes up under the status bar; `edge` says it
 * goes down to the bottom of the screen too, with no episode bar below.
 */
@Composable
private fun VideoArea(hud: PlayerHud, edge: Boolean, chrome: Boolean, rotation: Rotation?, landscape: Boolean, search: () -> Unit, tabletEpisodesVisible: Boolean, toggleTabletEpisodes: (() -> Unit)?, modifier: Modifier) {
    val player = LocalModel.current.player
    LaunchedEffect(hud.controlsVisible, hud.controlsPinned, hud.holding, player.paused, player.state, chrome) {
        if (!chrome || !hud.controlsVisible || hud.controlsPinned || hud.holding || player.paused || player.state != PlayerState.Playing) {
            return@LaunchedEffect
        }
        delay(3_000)
        if (chrome && hud.controlsVisible && !hud.controlsPinned && !hud.holding && !player.paused && player.state == PlayerState.Playing) {
            hud.controlsVisible = false
        }
    }
    Box(modifier.clipToBounds()) {
        VideoSurface(hud, Modifier.fillMaxSize().offset { IntOffset(0, hud.offset.roundToInt()) })
        if (!chrome) return@Box
        // Over the video alone; read as it draws, so a slide doesn't recompose the controls.
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                if (hud.brightness < 1f) drawRect(Color.Black, alpha = (1f - hud.brightness) * MAX_DIM)
            },
        )
        if (hud.seeking) {
            Spacer(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black, alpha = 0.52f) })
        }
        // Under the controls and below the top bar's row; it goes with the video as a swipe moves it.
        DanmakuLayer(
            Modifier.fillMaxSize().offset { IntOffset(0, hud.offset.roundToInt()) }
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
                .padding(top = 52.dp),
        )
        Gestures(hud, Modifier.fillMaxSize())
        PauseMark(Modifier.align(Alignment.Center))
        PlayerOverlay()
        SeekPreview(hud, Modifier.align(Alignment.Center))
        if (hud.diagnosticsVisible) {
            PlaybackStatsOverlay(player, Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = if (landscape) 12.dp else 76.dp))
        }
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = hud.controlsVisible,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(220)),
            ) {
                TopBar(hud, rotation, landscape, search, tabletEpisodesVisible, toggleTabletEpisodes)
            }
            SpeedHint(hud)
            Spacer(Modifier.weight(1f))
            // With nothing under the video, the bar keeps clear of the system's.
            AnimatedVisibility(
                visible = hud.controlsVisible,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(220)),
            ) {
                ProgressBar(
                    hud = hud,
                    modifier = if (edge) {
                        Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

/** VideoSurface shows the video, fitted to its frame, black around it. */
@Composable
private fun VideoSurface(hud: PlayerHud, modifier: Modifier) {
    val video = LocalVideo.current
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setBackgroundColor(android.graphics.Color.BLACK)
                player = video.exo
                hud.surfaceView = videoSurfaceView as? SurfaceView
            }
        },
        modifier = modifier,
        onRelease = {
            if (hud.surfaceView === it.videoSurfaceView) hud.surfaceView = null
            it.player = null
        },
    )
}

/** What a finger down on the video turned out to be, before a long press would. */
private sealed interface Touch {
    data object Up : Touch
    data class Moved(val by: Offset) : Touch
}

/**
 * Gestures reads the fingers on the video: a tap pauses, a swipe up or down
 * moves between episodes, one sideways adjusts brightness above or seeks
 * below, and a press and hold plays at double speed.
 */
@Composable
private fun Gestures(hud: PlayerHud, modifier: Modifier) {
    val player = LocalModel.current.player
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    Box(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                hud.controlsPinned = true
                try {
                val slop = viewConfiguration.touchSlop
                val touch = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var t: Touch? = null
                    while (t == null) {
                        val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        t = when {
                            c == null || !c.pressed -> Touch.Up
                            (c.position - down.position).getDistance() > slop -> Touch.Moved(c.position - down.position)
                            else -> null
                        }
                    }
                    t
                }
                when {
                    touch == null -> hold(down, player, hud, haptic)
                    touch == Touch.Up -> {
                        // When controls are hidden, the first tap only brings them back.
                        if (!hud.controlsVisible) hud.controlsVisible = true else player.togglePause()
                    }
                    touch is Touch.Moved && abs(touch.by.y) > abs(touch.by.x) -> swipe(down, player, hud, scope)
                    touch is Touch.Moved && down.position.y < size.height / 2f -> brighten(down, hud)
                    touch is Touch.Moved -> seekHorizontally(down, player, hud, scope)
                    else -> untilUp(down)
                }
                } finally {
                    hud.controlsPinned = false
                }
            }
        },
    )
}

private suspend fun AwaitPointerEventScope.untilUp(down: PointerInputChange) {
    while (true) {
        val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return
        if (!c.pressed) return
    }
}

/**
 * Plays at double speed while a finger holds the video; swiping down locks
 * it on, and swiping up in a later hold turns it off.
 */
private suspend fun AwaitPointerEventScope.hold(down: PointerInputChange, player: Player, hud: PlayerHud, haptic: HapticFeedback) {
    val far = 40.dp.toPx()
    player.beginHold()
    hud.toast = null
    hud.holding = true
    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    var lifted = false
    try {
        while (true) {
            val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!c.pressed) {
                lifted = true
                break
            }
            c.consume()
            val dy = c.position.y - down.position.y
            val swiped = if (player.doubleSpeed == DoubleSpeed.LOCKED) dy < -far else dy > far
            if (swiped != hud.swiped) {
                hud.swiped = swiped
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    } finally {
        val was = player.doubleSpeed
        player.endHold(swiped = lifted && hud.swiped)
        hud.holding = false
        hud.swiped = false
        if (was == DoubleSpeed.HELD && player.doubleSpeed == DoubleSpeed.LOCKED) {
            hud.say("已锁定 2 倍速，长按上滑可取消")
        } else if (was == DoubleSpeed.LOCKED && player.doubleSpeed == DoubleSpeed.OFF) {
            hud.say("已" + restoring(player.baseRate))
        }
    }
}

/**
 * Swiping up plays the next episode, and down the one before: the video
 * follows the finger, and goes on out if it went far or fast enough.
 */
private suspend fun AwaitPointerEventScope.swipe(down: PointerInputChange, player: Player, hud: PlayerHud, scope: CoroutineScope) {
    val tracker = VelocityTracker()
    tracker.addPosition(down.uptimeMillis, down.position)
    var dy = 0f
    var lifted = false
    while (true) {
        val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
        tracker.addPosition(c.uptimeMillis, c.position)
        if (!c.pressed) {
            lifted = true
            break
        }
        c.consume()
        dy = c.position.y - down.position.y
        val can = if (dy < 0) player.hasNext else player.hasPrevious
        hud.offset = if (can) dy else dy / 4 // nowhere to go: it gives a little
    }
    val d = if (dy < 0) 1 else -1
    val can = if (d > 0) player.hasNext else player.hasPrevious
    val h = size.height.toFloat()
    val vy = tracker.calculateVelocity().y
    val far = abs(dy) > h / 6 || (abs(vy) > 700.dp.toPx() && vy * dy > 0)
    if (!lifted || !can || !far) {
        scope.launch { animate(hud.offset, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)) { v, _ -> hud.offset = v } }
        if (lifted && !can && abs(dy) > 50.dp.toPx()) hud.say(if (d > 0) "已经是最后一集了" else "已经是第一集了")
        return
    }
    scope.launch {
        animate(hud.offset, if (dy < 0) -h else h, animationSpec = tween(200, easing = FastOutLinearInEasing)) { v, _ -> hud.offset = v }
        player.step(d)
        hud.offset = 0f // the screen black, until the next episode
    }
}

/** How dark the layer over the video gets at brightness 0, so the picture never goes quite black. */
private const val MAX_DIM = 0.8f

/** How far a finger has slid sideways from where it went down, as a fraction of the width; null once it lifts. */
private suspend fun AwaitPointerEventScope.slid(down: PointerInputChange): Float? {
    val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return null
    if (!c.pressed) return null
    c.consume()
    return (c.position.x - down.position.x) / size.width
}

/**
 * Sliding sideways in the upper half adjusts the picture brightness: to
 * the right is brighter, and a slide across the whole width goes from dark
 * to full.
 */
private suspend fun AwaitPointerEventScope.brighten(down: PointerInputChange, hud: PlayerHud) {
    val from = hud.brightness
    while (true) {
        val by = slid(down) ?: return
        hud.brightness = (from + by).coerceIn(0f, 1f)
        hud.say("亮度 ${(hud.brightness * 100).roundToInt()}%")
    }
}

/**
 * Horizontal swipes in the lower half scrub the episode. A full-screen-width
 * drag spans the episode timeline; while dragging, the video pauses, seeks
 * to preview real frames, and shows a centered thumbnail and progress line.
 * Releasing resumes only if playback was running before the gesture.
 */
private suspend fun AwaitPointerEventScope.seekHorizontally(
    down: PointerInputChange,
    player: Player,
    hud: PlayerHud,
    scope: CoroutineScope,
) {
    val video = player.video
    val duration = video.duration
    if (!duration.isFinite() || duration <= 0.0) {
        untilUp(down)
        return
    }

    val initial = video.position.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    val wasPlaying = video.wantsToPlay && !player.paused
    val width = size.width.toFloat().coerceAtLeast(1f)
    var target = initial
    var lifted = false
    var lastSeekAt = Long.MIN_VALUE

    hud.seeking = true
    hud.controlsVisible = true
    hud.seekPreviewStart = initial
    hud.seekPreviewPosition = initial
    hud.seekPreviewDuration = duration
    video.pause()

    try {
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                lifted = true
                break
            }
            change.consume()
            val delta = (change.position.x - down.position.x) / width * duration
            target = (initial + delta).coerceIn(0.0, duration)
            hud.seekPreviewPosition = target

            // Throttle decoder seeks while keeping the time/progress readout fluid.
            if (lastSeekAt == Long.MIN_VALUE || change.uptimeMillis - lastSeekAt >= 140L) {
                video.seek(target)
                lastSeekAt = change.uptimeMillis
                requestSeekPreviewFrame(hud, scope)
            }
        }
    } finally {
        video.seek(if (lifted) target else initial)
        hud.seeking = false
        hud.seekPreviewRequestId += 1L // invalidate any frame capture still in flight
        val old = hud.seekPreviewBitmap
        hud.seekPreviewBitmap = null
        releasePreviewBitmap(old, scope)
        if (wasPlaying) video.play()
    }
}

/** Captures a small real frame from PlayerView's SurfaceView after a seek has rendered. */
private fun requestSeekPreviewFrame(hud: PlayerHud, scope: CoroutineScope) {
    val requestId = ++hud.seekPreviewRequestId
    scope.launch {
        delay(110)
        val surface = hud.surfaceView ?: return@launch
        if (!hud.seeking || !surface.isAttachedToWindow || surface.width <= 0 || surface.height <= 0) return@launch

        // Keep the captured bitmap's aspect ratio: portrait shorts stay portrait, landscape stays landscape.
        val aspect = surface.width.toFloat() / surface.height.toFloat()
        val bitmapWidth = if (aspect >= 1f) 320 else (320 * aspect).roundToInt().coerceAtLeast(1)
        val bitmapHeight = if (aspect >= 1f) (320 / aspect).roundToInt().coerceAtLeast(1) else 320
        val frame = runCatching {
            Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        }.getOrNull() ?: return@launch

        val callback = PixelCopy.OnPixelCopyFinishedListener { result ->
            if (result == PixelCopy.SUCCESS && hud.seeking && hud.seekPreviewRequestId == requestId) {
                val old = hud.seekPreviewBitmap
                hud.seekPreviewBitmap = frame
                releasePreviewBitmap(old, scope)
            } else if (!frame.isRecycled) {
                frame.recycle()
            }
        }
        val requested = runCatching {
            PixelCopy.request(surface, frame, callback, Handler(Looper.getMainLooper()))
        }.isSuccess
        if (!requested && !frame.isRecycled) frame.recycle()
    }
}

/** Waits one frame before recycling so Compose cannot draw a recycled preview bitmap. */
private fun releasePreviewBitmap(bitmap: Bitmap?, scope: CoroutineScope) {
    if (bitmap == null) return
    scope.launch {
        withFrameMillis { }
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

private val danmakuStyle = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Light)

/**
 * DanmakuLayer flies the episode's comments right to left across the top
 * of the video, white with a dark edge, as [Lanes] places them. Where they
 * are follows the video's time: read every frame while it plays, and now
 * and then otherwise, which a drag along the progress bar may move. Nothing
 * here takes a touch, so the video's gestures work through it.
 */
@Composable
private fun DanmakuLayer(modifier: Modifier) {
    val player = LocalModel.current.player
    val comments = player.danmaku
    if (comments.isEmpty() || !(player.state == PlayerState.Playing || player.state == PlayerState.Buffering)) return
    val video = player.video
    val moving = player.state == PlayerState.Playing && !player.paused
    var now by remember { mutableLongStateOf(-1L) } // ms into the episode; -1 without one
    LaunchedEffect(moving) {
        while (true) {
            val t = video.position
            now = if (t.isFinite()) (t * 1000).toLong() else -1L
            if (moving) withFrameMillis {} else delay(250)
        }
    }
    val measurer = rememberTextMeasurer()
    Spacer(
        modifier.drawWithCache {
            val em = danmakuStyle.fontSize.toPx()
            val lane = em * 1.6f
            val lanes = Lanes(comments, size.width / em)
            val texts = arrayOfNulls<TextLayoutResult>(lanes.flights.size) // each measured as it first shows
            val edge = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round)
            val edgeColor = Color.Black.copy(alpha = 0.3f)
            val glow = Shadow(color = Color.Black.copy(alpha = 0.28f), blurRadius = 2f * density)
            onDrawBehind {
                val ms = now
                if (ms < 0) return@onDrawBehind
                lanes.at(ms) { i, x ->
                    val f = lanes.flights[i]
                    val alpha = lanes.alpha(f, x)
                    if (alpha <= 0f) return@at
                    val text = texts[i] ?: measurer.measure(f.comment.text, danmakuStyle, softWrap = false, maxLines = 1).also { texts[i] = it }
                    val at = Offset(x * em, f.lane * lane + (lane - text.size.height) / 2)
                    drawText(text, edgeColor, at, alpha = alpha, drawStyle = edge)
                    drawText(text, topLeft = at, alpha = alpha, shadow = glow, drawStyle = Fill)
                }
            }
        },
    )
}

/** PauseMark says the video is paused; a tap anywhere on it plays on. */
@Composable
private fun PauseMark(modifier: Modifier) {
    val player = LocalModel.current.player
    if (player.paused && (player.state == PlayerState.Playing || player.state == PlayerState.Buffering)) {
        Icon(Icons.Filled.PlayArrow, contentDescription = "已暂停", tint = Color.White.copy(alpha = 0.85f), modifier = modifier.size(76.dp))
    }
}

/**
 * PlayerOverlay is what covers the video while it isn't playing: a spinner
 * while it loads or waits for more, or what went wrong. Swipes go through
 * it, to the episodes either side.
 */
@Composable
private fun PlayerOverlay() {
    val player = LocalModel.current.player
    when (val s = player.state) {
        PlayerState.Resolving -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        PlayerState.Buffering -> if (!player.paused) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        }
        PlayerState.ResettingDevice -> Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = Color.White)
                Text("正在刷新匿名设备并重新获取播放信息…", color = Color.White, textAlign = TextAlign.Center)
            }
        }
        is PlayerState.Failed -> {
            val context = LocalContext.current
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.fillMaxWidth().widthIn(max = 560.dp).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(s.message, color = Color.White, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    val d = s.diagnostic
                    if (d != null) {
                        Text(
                            "完整请求过程、响应信息和设备环境已收集",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("开心果播放诊断", d.toReport()))
                                AndroidToast.makeText(context, "完整诊断信息已复制", AndroidToast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("复制完整诊断信息") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = player::retry) { Text("重试") }
                        if (player.hasNext) OutlinedButton(onClick = { player.step(1) }) { Text("下一集") }
                    }
                    if (d?.canRefreshDevice == true) {
                        OutlinedButton(onClick = player::retryWithFreshDevice) {
                            Text("刷新匿名设备并重试")
                        }
                    }
                }
            }
        }
        else -> {}
    }
}

@Composable
private fun DiagnosticValue(label: String, value: String) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(label, color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Text(value, color = Color.White, fontSize = 12.sp)
    }
}

// MARK: - Over the video

/** TopBar is what plays, and how fast, over the top of the video, with the way out, to turn the screen, to search and to more. */
@Composable
private fun TopBar(hud: PlayerHud, rotation: Rotation?, landscape: Boolean, search: () -> Unit, tabletEpisodesVisible: Boolean, toggleTabletEpisodes: (() -> Unit)?) {
    val player = LocalModel.current.player
    val config = LocalConfiguration.current
    val showFullscreenClock = landscape || (config.smallestScreenWidthDp >= 600 && !tabletEpisodesVisible)
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)))) {
        Row(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 4.dp, end = 4.dp, bottom = 20.dp).height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = player::leave) {
                Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "退出播放", tint = Color.White)
            }
            Column(Modifier.weight(1f).padding(end = if (showFullscreenClock) 236.dp else 0.dp)) {
                Text("第 ${player.number} 集", color = Color.White, style = MaterialTheme.typography.titleMedium.merge(shadowed), fontWeight = FontWeight.SemiBold)
                player.page?.series?.title?.let {
                    Text(it, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall.merge(shadowed), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SpeedMenu(hud)
            if (rotation != null) RotateButton(rotation, landscape)
            IconButton(onClick = search) { Icon(Icons.Filled.Search, contentDescription = "搜索", tint = Color.White) }
            if (toggleTabletEpisodes != null) {
                IconButton(onClick = toggleTabletEpisodes) {
                    Icon(
                        Icons.Filled.GridView,
                        contentDescription = if (tabletEpisodesVisible) "隐藏选集" else "显示选集",
                        tint = Color.White,
                    )
                }
            }
            TvHandoffButton(model = LocalModel.current)
            MoreMenu(hud)
        }
        if (showFullscreenClock) {
            Box(
                Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
                    .height(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                ClockBatteryPill()
            }
        }
    }
}

/** A minimalist clock and battery badge shown only with full-screen playback controls. */
@Composable
private fun ClockBatteryPill() {
    val context = LocalContext.current
    var currentTime by remember { mutableStateOf(java.time.LocalTime.now().format(clockFormat)) }
    var batteryPercent by remember { mutableStateOf<Int?>(null) }
    var charging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val nowMillis = System.currentTimeMillis()
            currentTime = java.time.LocalTime.now().format(clockFormat)
            delay(60_000L - nowMillis % 60_000L)
        }
    }

    DisposableEffect(context) {
        fun updateBattery(intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                batteryPercent = (level * 100f / scale).roundToInt().coerceIn(0, 100)
            }
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val externallyPowered = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                (status == BatteryManager.BATTERY_STATUS_FULL && externallyPowered)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateBattery(intent)
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        updateBattery(sticky)
        onDispose { context.unregisterReceiver(receiver) }
    }

    Row(
        Modifier.clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            currentTime,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        batteryPercent?.let { percent ->
            val levelTint = batteryLevelColor(percent)
            val iconTint = batteryIconColor(percent, charging)
            HorizontalBatteryIcon(percent = percent, tint = iconTint, charging = charging)
            Text(
                "$percent%",
                color = levelTint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** Horizontal battery with a true percentage fill and an optional charging bolt. */
@Composable
private fun HorizontalBatteryIcon(percent: Int, tint: Color, charging: Boolean) {
    Canvas(Modifier.size(width = 26.dp, height = 14.dp)) {
        val border = 1.25.dp.toPx()
        val terminalWidth = 2.2.dp.toPx()
        val bodyLeft = border / 2f
        val bodyTop = border / 2f
        val bodyRight = size.width - terminalWidth
        val bodyBottom = size.height - border / 2f
        val bodyWidth = bodyRight - bodyLeft
        val bodyHeight = bodyBottom - bodyTop
        val outlineColor = tint

        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(2.2.dp.toPx()),
            style = Stroke(width = border),
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(bodyRight, size.height * 0.32f),
            size = Size(terminalWidth, size.height * 0.36f),
            cornerRadius = CornerRadius(terminalWidth * 0.3f),
        )

        val inset = border + 1.dp.toPx()
        val innerLeft = bodyLeft + inset
        val innerTop = bodyTop + inset
        val innerWidth = (bodyWidth - inset * 2f).coerceAtLeast(0f)
        val innerHeight = (bodyHeight - inset * 2f).coerceAtLeast(0f)
        if (innerWidth > 0f && innerHeight > 0f) {
            val innerRadius = CornerRadius(1.dp.toPx())
            drawRoundRect(
                color = Color.White.copy(alpha = 0.18f),
                topLeft = Offset(innerLeft, innerTop),
                size = Size(innerWidth, innerHeight),
                cornerRadius = innerRadius,
            )

            val fillWidth = innerWidth * (percent.coerceIn(0, 100) / 100f)
            if (fillWidth > 0f) {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(innerLeft, innerTop),
                    size = Size(fillWidth, innerHeight),
                    cornerRadius = innerRadius,
                )
            }

            if (charging) {
                val centerX = innerLeft + innerWidth * 0.52f
                val bolt = androidx.compose.ui.graphics.Path().apply {
                    moveTo(centerX + innerWidth * 0.14f, innerTop + innerHeight * 0.04f)
                    lineTo(centerX - innerWidth * 0.15f, innerTop + innerHeight * 0.52f)
                    lineTo(centerX + innerWidth * 0.01f, innerTop + innerHeight * 0.52f)
                    lineTo(centerX - innerWidth * 0.08f, innerTop + innerHeight * 0.96f)
                    lineTo(centerX + innerWidth * 0.20f, innerTop + innerHeight * 0.41f)
                    lineTo(centerX + innerWidth * 0.05f, innerTop + innerHeight * 0.41f)
                    close()
                }
                // A dark outline and white center keep the bolt visible on every fill color.
                drawPath(
                    path = bolt,
                    color = Color(0xFF123E2E),
                    style = Stroke(width = 0.9.dp.toPx(), join = StrokeJoin.Round),
                )
                drawPath(path = bolt, color = Color.White)
            }
        }
    }
}

/**
 * RotateButton turns the screen on its side for a video wider than tall,
 * and upright again: on phones, where on its side the screen shows the
 * video alone. It stays while the screen is held on its side, so there is
 * always a way back, even to a series shot upright.
 */
@Composable
private fun RotateButton(rotation: Rotation, landscape: Boolean) {
    val player = LocalModel.current.player
    if (LocalConfiguration.current.smallestScreenWidthDp >= 600 || !(player.wide || rotation.held == true)) return
    IconButton(onClick = { rotation.turn(landscape = !landscape) }) {
        Icon(Icons.Filled.ScreenRotation, contentDescription = if (landscape) "竖屏观看" else "横屏观看", tint = Color.White)
    }
}

/** A speed as the player writes it: 1.25, 2. */
private fun rateText(r: Float): String = if (r == r.toInt().toFloat()) "${r.toInt()}" else "$r"

/** What going back from double speed says: to the speed the viewer chose. */
private fun restoring(rate: Float): String = if (rate == 1f) "恢复正常速度" else "恢复 ${rateText(rate)} 倍速"

/** SpeedMenu shows the speed the video plays at, and changes it. */
@Composable
private fun SpeedMenu(hud: PlayerHud) {
    val player = LocalModel.current.player
    var open by remember { mutableStateOf(false) }
    fun close() {
        open = false
        hud.controlsPinned = false
    }
    Box {
        Row(
            Modifier.clip(CircleShape).clickable { open = true; hud.controlsPinned = true }.height(44.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (player.speed == 1f) "倍速" else "${rateText(player.speed)}x", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(expanded = open, onDismissRequest = { close() }) {
            SpeedItems(player) { close() }
        }
    }
}

@Composable
private fun SpeedItems(player: Player, done: () -> Unit) {
    for (s in Player.speeds.reversed()) {
        Choice(if (s == 1f) "1x 正常" else "${rateText(s)}x", player.speed == s) {
            player.chooseSpeed(s)
            done()
        }
    }
}

@Composable
private fun Choice(label: String, on: Boolean, pick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = { if (on) Icon(Icons.Filled.Check, contentDescription = null) },
        onClick = pick,
    )
}

private enum class More { MAIN, SPEED, QUALITY, SLEEP }

/**
 * MoreMenu sets how the episode plays: its speed, its definition, when to
 * stop and whether comments fly over it; and saves the series, or opens its
 * page.
 */
@Composable
private fun MoreMenu(hud: PlayerHud) {
    val model = LocalModel.current
    val router = LocalRouter.current
    val activity = LocalActivity.current as? MainActivity
    val player = model.player
    var open by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(More.MAIN) }
    fun close() {
        open = false
        hud.controlsPinned = false
    }
    Box {
        IconButton(onClick = {
            page = More.MAIN
            open = true
            hud.controlsPinned = true
        }) { Icon(Icons.Filled.MoreHoriz, contentDescription = "更多", tint = Color.White) }
        DropdownMenu(expanded = open, onDismissRequest = { close() }) {
            when (page) {
                More.MAIN -> {
                    Entry("倍速", Icons.Filled.Speed, if (player.speed == 1f) "正常" else "${rateText(player.speed)}x") { page = More.SPEED }
                    if (player.qualities.isNotEmpty()) {
                        Entry("清晰度", Icons.Filled.HighQuality, "${player.quality}P") { page = More.QUALITY }
                    }
                    Entry("定时关闭", Icons.Filled.Bedtime, sleepLine(player)) { page = More.SLEEP }
                    DropdownMenuItem(
                        text = { Text("播放诊断信息") },
                        leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                        trailingIcon = { Switch(checked = hud.diagnosticsVisible, onCheckedChange = null) },
                        onClick = { hud.diagnosticsVisible = !hud.diagnosticsVisible; close() },
                    )
                    DropdownMenuItem(
                        text = { Text("弹幕") },
                        leadingIcon = { Icon(Icons.Filled.Subtitles, contentDescription = null) },
                        trailingIcon = { Switch(checked = player.showDanmaku, onCheckedChange = null) },
                        onClick = { player.chooseDanmaku(!player.showDanmaku) },
                    )
                    val sp = player.page
                    val series = sp?.series
                    if (sp != null && series != null) {
                        HorizontalDivider()
                        val saved = model.favorites.has(sp.id)
                        DropdownMenuItem(
                            text = { Text(favLabel(saved) + "这部剧") },
                            leadingIcon = { Icon(if (saved) Icons.Outlined.StarOutline else Icons.Filled.Star, contentDescription = null) },
                            onClick = {
                                model.favorites.toggle(Fav.of(Card.of(series)))
                                close()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("剧集详情") },
                            leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                            onClick = {
                                close()
                                player.leave()
                                if (router.top != Route.Series(sp.id)) router.open(Route.Series(sp.id))
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("检查更新") },
                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        onClick = {
                            close()
                            activity?.checkForUpdatesManually()
                        },
                    )
                }
                More.SPEED -> {
                    Heading("倍速") { page = More.MAIN }
                    SpeedItems(player) { close() }
                }
                More.QUALITY -> {
                    Heading("清晰度") { page = More.MAIN }
                    for (q in player.qualities) {
                        Choice("${q}P", q == player.quality) {
                            player.chooseQuality(q)
                            close()
                        }
                    }
                }
                More.SLEEP -> {
                    Heading("定时关闭") { page = More.MAIN }
                    val choices = listOf(Sleep.Off to "不开启", Sleep.EndOfEpisode to "播完本集") +
                        listOf(15, 30, 60, 90).map { Sleep.Minutes(it) to "$it 分钟" }
                    for ((s, label) in choices) {
                        Choice(label, player.sleep == s) {
                            player.chooseSleep(s)
                            close()
                        }
                    }
                }
            }
        }
    }
}

/** A line of the menu that opens a list of choices, the one made beside it. */
@Composable
private fun Entry(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, open: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = { Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) },
        onClick = open,
    )
}

/** The head of a list of choices, which goes back to the menu. */
@Composable
private fun Heading(label: String, back: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontWeight = FontWeight.SemiBold) },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "返回", modifier = Modifier.size(16.dp)) },
        onClick = back,
    )
    HorizontalDivider()
}

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

/** When the sleep timer stops the video: "播完本集", "22:30 停止". */
private fun sleepLine(player: Player): String = when (player.sleep) {
    Sleep.Off -> "未开启"
    Sleep.EndOfEpisode -> "播完本集"
    is Sleep.Minutes -> (player.sleepAt?.atZone(ZoneId.systemDefault())?.format(clockFormat) ?: "") + " 停止"
}

/** Seconds as minutes and seconds: 1:05. */
private fun clock(s: Double): String {
    if (!s.isFinite() || s <= 0) return "0:00"
    val n = s.toInt()
    return "%d:%02d".format(n / 60, n % 60)
}

/** Redfruit-style centre preview while seeking: captured frame, direction, time, and progress. */
@Composable
private fun SeekPreview(hud: PlayerHud, modifier: Modifier) {
    if (!hud.seeking) return

    val duration = hud.seekPreviewDuration.coerceAtLeast(0.0)
    val position = hud.seekPreviewPosition.coerceIn(0.0, duration)
    val delta = position - hud.seekPreviewStart
    val fraction = if (duration > 0.0) (position / duration).coerceIn(0.0, 1.0).toFloat() else 0f

    Column(
        modifier.widthIn(max = 260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xE6111111))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            (if (delta >= 0.0) "快进 " else "后退 ") + clock(kotlin.math.abs(delta)),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth()
                .aspectRatio(if (LocalModel.current.player.wide) 16f / 9f else 9f / 16f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF242424)),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = hud.seekPreviewBitmap
            if (bitmap != null && !bitmap.isRecycled) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "拖动进度预览画面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    if (delta >= 0.0) Icons.Filled.FastForward else Icons.Filled.Replay10,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.24f)),
        ) {
            Box(
                Modifier.fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(Color(0xFFFF4242)),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(clock(position), color = Color.White, style = MaterialTheme.typography.labelSmall)
            Text(clock(duration), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** ProgressBar is how far the episode has got, along the bottom of the video; dragging along it moves there. */
@Composable
private fun ProgressBar(hud: PlayerHud, modifier: Modifier) {
    val player = LocalModel.current.player
    val video = player.video
    var dragged by remember { mutableStateOf<Float?>(null) } // where a drag is, as a fraction
    var at by remember { mutableDoubleStateOf(0.0) }
    var position by remember { mutableDoubleStateOf(0.0) }
    var total by remember { mutableDoubleStateOf(0.0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        while (true) {
            val d = video.duration
            total = if (d.isFinite() && d > 0) d else 0.0
            val t = video.position
            position = if (t.isFinite() && t >= 0) t else 0.0
            at = if (total > 0 && t.isFinite()) (t / total).coerceIn(0.0, 1.0) else 0.0
            delay(250)
        }
    }
    val shown = dragged ?: at.toFloat()
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { player.skip(-10.0) }, enabled = total > 0, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Replay10, contentDescription = "快退 10 秒", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Text(clock(position) + " / " + clock(total), color = Color.White, style = MaterialTheme.typography.labelMedium.merge(shadowed), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(onClick = { player.skip(10.0) }, enabled = total > 0, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Forward10, contentDescription = "快进 10 秒", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        dragged?.let { f ->
            Text(
                "${clock(f * total)} / ${clock(total)}",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
        Canvas(
            Modifier.fillMaxWidth().height(28.dp).pointerInput(total) {
                if (total <= 0) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    hud.controlsPinned = true
                    try {
                        dragged = (down.position.x / size.width).coerceIn(0f, 1f)
                        while (true) {
                            val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            c.consume()
                            dragged = (c.position.x / size.width).coerceIn(0f, 1f)
                        }
                        val f = dragged ?: return@awaitEachGesture
                        video.seek(f * total)
                        // Keeps the bar there until the video has moved.
                        scope.launch {
                            delay(400)
                            dragged = null
                        }
                    } finally {
                        hud.controlsPinned = false
                    }
                }
            },
        ) {
            if (total <= 0) return@Canvas
            val active = dragged != null
            val h = (if (active) 4.dp else 2.dp).toPx()
            val knob = (if (active) 14.dp else 7.dp).toPx()
            val y = size.height / 2
            val w = size.width
            drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(0f, y - h / 2), Size(w, h), CornerRadius(h / 2))
            drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(0f, y - h / 2), Size(w * shown, h), CornerRadius(h / 2))
            drawCircle(Color.White, knob / 2, Offset((w * shown).coerceIn(knob / 2, w - knob / 2), y))
        }
    }
}


/** One 500 ms display sample written into a fixed column of the 20-second sweep display. */
private data class PlaybackStatsSample(
    val speedBps: Long = 0L,
    val networkBytes: Long = 0L,
    val bufferSeconds: Float = 0f,
)

private const val STATS_SAMPLE_INTERVAL_MS = 500L
private const val STATS_SAMPLE_COUNT = 40

/** Live Stats for nerds panel. Each graph writes only at the moving sweep head. */
@Composable
private fun PlaybackStatsOverlay(player: Player, modifier: Modifier) {
    val video = LocalVideo.current
    val diagnostics = com.kadaliao.kaixinguo.playback.PlaybackDiagnostics
    var snapshot by remember { mutableStateOf(diagnostics.snapshot()) }
    var bufferSeconds by remember(player.number) { mutableFloatStateOf(0f) }
    val graphHistory = remember(player.number) {
        mutableStateOf(List(STATS_SAMPLE_COUNT) { PlaybackStatsSample() })
    }
    val sweepPosition = remember(player.number) { mutableFloatStateOf(0f) }

    LaunchedEffect(player.number) {
        var sampleIndex = 0
        var smoothedGraphSpeedBps = 0.0
        while (true) {
            val currentSnapshot = diagnostics.snapshot()
            snapshot = currentSnapshot
            val current = video.exo.currentPosition
            val buffered = video.exo.bufferedPosition
            bufferSeconds = if (buffered >= current && buffered >= 0L) {
                ((buffered - current) / 1000f).coerceAtLeast(0f)
            } else {
                0f
            }

            // Connection Speed is a rolling connection estimate, not the bytes
            // in this exact 500 ms slot. CDN requests arrive in bursts, so raw
            // per-slot bytes created empty gaps and isolated spikes in the curve.
            // Smooth changing estimates; after the estimate expires, fade gently
            // toward the baseline instead of suddenly breaking the blue trace.
            val reportedSpeed = currentSnapshot.connectionSpeedBps.toDouble()
            smoothedGraphSpeedBps = when {
                reportedSpeed > 0.0 -> if (smoothedGraphSpeedBps <= 0.0) {
                    reportedSpeed
                } else {
                    smoothedGraphSpeedBps * 0.62 + reportedSpeed * 0.38
                }
                else -> smoothedGraphSpeedBps * 0.78
            }
            if (smoothedGraphSpeedBps < 25_000.0) smoothedGraphSpeedBps = 0.0
            val networkBytes = currentSnapshot.networkSamples.takeLast(2).sum()
            val sample = PlaybackStatsSample(
                speedBps = smoothedGraphSpeedBps.toLong(),
                networkBytes = networkBytes,
                bufferSeconds = bufferSeconds,
            )
            graphHistory.value = graphHistory.value.toMutableList().also { it[sampleIndex] = sample }

            val frameStart = withFrameMillis { it }
            var elapsed = 0L
            while (elapsed < STATS_SAMPLE_INTERVAL_MS) {
                val frame = withFrameMillis { it }
                elapsed = (frame - frameStart).coerceAtLeast(0L)
                sweepPosition.floatValue = sampleIndex +
                    (elapsed.toFloat() / STATS_SAMPLE_INTERVAL_MS).coerceIn(0f, 1f)
            }
            sampleIndex = (sampleIndex + 1) % STATS_SAMPLE_COUNT
            if (sampleIndex == 0) sweepPosition.floatValue = 0f
        }
    }

    val width = snapshot.width.takeIf { it > 0 } ?: player.sourceWidth
    val height = snapshot.height.takeIf { it > 0 } ?: player.sourceHeight
    val currentRes = if (width > 0 && height > 0) "$width×$height" else "${player.quality}P"
    val optimal = player.qualities.maxOrNull()?.let { "${it}P" } ?: "—"
    val fpsNumber = if (snapshot.frameRate > 0f) String.format(Locale.US, "%.0f", snapshot.frameRate) else ""
    val fps = if (fpsNumber.isNotEmpty()) "  ${fpsNumber}fps" else ""
    val videoCodec = snapshot.videoCodec.ifBlank { player.selectedCodec.ifBlank { "—" } }
    val audioCodec = snapshot.audioCodec
    val codecs = if (audioCodec.isNotBlank()) "$videoCodec · $audioCodec" else videoCodec
    val decoderKind = when (snapshot.decoderHardware) {
        true -> "硬件解码"
        false -> "软件解码"
        null -> "等待解码器"
    }
    val decoder = snapshot.decoderName.takeIf { it.isNotBlank() }?.let { "$decoderKind · $it" } ?: decoderKind
    val speed = if (snapshot.connectionSpeedBps > 0) {
        String.format(Locale.US, "%.1f Mbps", snapshot.connectionSpeedBps / 1_000_000.0)
    } else {
        "等待数据"
    }
    val netKb = snapshot.networkBytesLast20Seconds / 1024.0
    val net = if (netKb >= 1024.0) {
        String.format(Locale.US, "%.1f MB/20s", netKb / 1024.0)
    } else {
        String.format(Locale.US, "%.0f KB/20s", netKb)
    }
    val buffer = String.format(Locale.US, "%.1f s", bufferSeconds)

    Column(
        modifier.widthIn(max = 370.dp).fillMaxWidth(0.96f)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
            .background(Color(0xE8101010))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("Stats for nerds", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        StatsTextRow("Current / Optimal Res", "$currentRes$fps / $optimal")
        StatsTextRow("Codecs", codecs)
        StatsTextRow("Decoder", decoder)
        StatsGraphRow("Connection Speed", speed, graphHistory.value, StatsGraphKind.SPEED, sweepPosition.floatValue)
        StatsGraphRow("Network Activity", net, graphHistory.value, StatsGraphKind.NETWORK, sweepPosition.floatValue)
        StatsGraphRow("Buffer Health", buffer, graphHistory.value, StatsGraphKind.BUFFER, sweepPosition.floatValue)
        StatsTextRow("Dropped Frames", snapshot.droppedFrames.toString())
    }
}

@Composable
private fun StatsTextRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.83f), fontSize = 9.sp, modifier = Modifier.width(112.dp), maxLines = 1)
        Text(value, color = Color.White, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

private enum class StatsGraphKind { SPEED, NETWORK, BUFFER }

/** These thresholds apply to the aggregated 500 ms display slot. */
private fun networkActivityColor(bytes: Long): Color = when {
    bytes <= 0L -> Color(0xFF262626)
    bytes < 20L * 1024L -> Color(0xFFF44336)
    bytes < 40L * 1024L -> Color(0xFFFF7043)
    bytes < 80L * 1024L -> Color(0xFFFFEE58)
    bytes < 160L * 1024L -> Color(0xFFD4E157)
    else -> Color(0xFF9CCC65)
}

private fun bufferHealthColor(seconds: Float): Color = when {
    seconds <= 0f -> Color(0xFF262626)
    seconds < 15f -> Color(0xFFFFEE58)
    seconds < 30f -> Color(0xFFD4E157)
    seconds < 60f -> Color(0xFF26A69A)
    seconds < 90f -> Color(0xFF2196F3)
    else -> Color.White
}

/**
 * The three diagnostics use distinct visual encodings: a continuous throughput
 * trace, spaced network activity peaks, and a filled buffer-health curve.
 * History has fixed columns; the black sweep cursor only overlays the live edge.
 */
@Composable
private fun StatsGraphRow(
    label: String,
    value: String,
    samples: List<PlaybackStatsSample>,
    kind: StatsGraphKind,
    sweepPosition: Float,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.83f), fontSize = 9.sp, modifier = Modifier.width(112.dp), maxLines = 1)
        Canvas(
            Modifier.weight(1f).height(14.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(1.dp))
        ) {
            drawRect(Color(0xFF181818))
            drawLine(
                Color(0xFF343434),
                Offset(0f, size.height - 0.5.dp.toPx()),
                Offset(size.width, size.height - 0.5.dp.toPx()),
                strokeWidth = 0.7.dp.toPx(),
            )

            val count = samples.size.coerceAtLeast(1)
            val slot = size.width / count
            val gap = 0.8.dp.toPx()

            when (kind) {
                StatsGraphKind.SPEED -> {
                    // A single blue hue; brightness (not hue or bars) carries the
                    // speed history. Keep a dim continuous baseline while idle.
                    val maxSpeedBps = 80_000_000.0
                    val floorSpeedBps = 100_000.0
                    val maxLog = kotlin.math.ln(1.0 + maxSpeedBps / floorSpeedBps)
                    val ratios = samples.map { sample ->
                        if (sample.speedBps <= 0L) 0f else {
                            (kotlin.math.ln(1.0 + sample.speedBps.toDouble() / floorSpeedBps) / maxLog)
                                .toFloat().coerceIn(0f, 1f)
                        }
                    }
                    val baselineY = size.height - 0.65.dp.toPx()
                    val curveHeight = (size.height - 1.4.dp.toPx()).coerceAtLeast(1f)
                    val points = ratios.mapIndexed { index, ratio ->
                        Offset((index.toFloat() + 0.5f) * slot, baselineY - ratio * curveHeight)
                    }
                    val speedBlue = Color(0xFF42A5F5)
                    drawLine(
                        speedBlue.copy(alpha = 0.18f),
                        Offset(0f, baselineY),
                        Offset(size.width, baselineY),
                        strokeWidth = 0.8.dp.toPx(),
                    )

                    // Catmull-Rom-style cubic segments round off abrupt sample changes.
                    // The entire time series is drawn, including zero/idle samples.
                    for (index in 0 until points.lastIndex) {
                        val p0 = points[(index - 1).coerceAtLeast(0)]
                        val p1 = points[index]
                        val p2 = points[index + 1]
                        val p3 = points[(index + 2).coerceAtMost(points.lastIndex)]
                        val dx = p2.x - p1.x
                        val control1Y = (p1.y + (p2.y - p0.y) / 6f).coerceIn(0f, size.height)
                        val control2Y = (p2.y - (p3.y - p1.y) / 6f).coerceIn(0f, size.height)
                        val curve = androidx.compose.ui.graphics.Path().apply {
                            moveTo(p1.x, p1.y)
                            cubicTo(
                                p1.x + dx / 3f, control1Y,
                                p2.x - dx / 3f, control2Y,
                                p2.x, p2.y,
                            )
                        }
                        val intensity = ((ratios[index] + ratios[index + 1]) / 2f).coerceIn(0f, 1f)
                        drawPath(
                            curve,
                            speedBlue.copy(alpha = 0.08f + intensity * 0.12f),
                            style = Stroke(
                                width = (2.6f + intensity * 1.0f).dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                        drawPath(
                            curve,
                            speedBlue.copy(alpha = 0.32f + intensity * 0.68f),
                            style = Stroke(
                                width = (1.15f + intensity * 0.45f).dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }
                }

                StatsGraphKind.NETWORK -> {
                    // A fixed 2 MiB / 500 ms reference scale prevents typical
                    // CDN bursts from saturating every column. Square-root scaling
                    // makes smaller real transfers visible without capping them.
                    val maxBytesPerSlot = 2L * 1024L * 1024L
                    val barWidth = (slot * 0.62f).coerceAtLeast(1.dp.toPx()).coerceAtMost(slot)
                    samples.forEachIndexed { index, sample ->
                        val bytes = sample.networkBytes
                        if (bytes > 0L) {
                            val normalized = (bytes.toDouble() / maxBytesPerSlot).coerceIn(0.0, 1.0)
                            val ratio = kotlin.math.sqrt(normalized).toFloat()
                            val barHeight = (size.height * ratio).coerceAtLeast(0.65.dp.toPx()).coerceAtMost(size.height)
                            val x = index * slot + (slot - barWidth) / 2f
                            drawRect(
                                networkActivityColor(bytes),
                                Offset(x, size.height - barHeight),
                                Size((barWidth - gap * 0.15f).coerceAtLeast(0.8.dp.toPx()), barHeight),
                            )
                        }
                    }
                }

                StatsGraphKind.BUFFER -> {
                    val maxSeconds = 90f
                    for (index in 1 until samples.size) {
                        val previous = samples[index - 1].bufferSeconds.coerceIn(0f, maxSeconds)
                        val current = samples[index].bufferSeconds.coerceIn(0f, maxSeconds)
                        if (previous <= 0f && current <= 0f) continue

                        val x1 = (index - 1 + 0.5f) * slot
                        val x2 = (index + 0.5f) * slot
                        val y1 = size.height - previous / maxSeconds * size.height
                        val y2 = size.height - current / maxSeconds * size.height
                        val color = bufferHealthColor(maxOf(previous, current))
                        val area = androidx.compose.ui.graphics.Path().apply {
                            moveTo(x1, size.height)
                            lineTo(x1, y1)
                            lineTo(x2, y2)
                            lineTo(x2, size.height)
                            close()
                        }
                        drawPath(area, color.copy(alpha = 0.16f))
                        drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.7.dp.toPx())
                    }
                }
            }

            val cursor = (sweepPosition % count.toFloat()).coerceIn(0f, count.toFloat())
            val cursorWidth = 1.3.dp.toPx()
            val cursorX = (cursor / count) * size.width
            val safeCursorX = cursorX.coerceIn(0f, (size.width - cursorWidth).coerceAtLeast(0f))
            drawRect(Color(0xFF080808), Offset(safeCursorX, 0f), Size(cursorWidth, size.height))
            drawLine(
                Color.White.copy(alpha = 0.32f),
                Offset((safeCursorX + cursorWidth).coerceAtMost(size.width), 0f),
                Offset((safeCursorX + cursorWidth).coerceAtMost(size.width), size.height),
                strokeWidth = 0.6.dp.toPx(),
            )
        }
        Text(value, color = Color.White, fontSize = 9.sp, modifier = Modifier.width(62.dp).padding(start = 4.dp), textAlign = TextAlign.End, maxLines = 1)
    }
}

/**
 * SpeedHint shows a press and hold on the video at work: double speed, and
 * which way to swipe to lock it on or turn it off; and, for a moment, what
 * a swipe did.
 */
@Composable
private fun SpeedHint(hud: PlayerHud) {
    val player = LocalModel.current.player
    val toast = hud.toast
    LaunchedEffect(toast) {
        if (toast == null) return@LaunchedEffect
        delay(2000)
        if (hud.toast === toast) hud.toast = null
    }
    Box(Modifier.fillMaxWidth().padding(top = 12.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(hud.holding, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
            val locked = player.doubleSpeed == DoubleSpeed.LOCKED
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (locked) "已锁定 2 倍速" else "2 倍速播放中", if (locked) Icons.Filled.Lock else Icons.Filled.FastForward)
                SwipeHint(locked, hud.swiped, player.baseRate)
            }
        }
        if (!hud.holding && toast != null) Pill(toast.text)
    }
}

@Composable
private fun Pill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Which way to swipe, or, once swiped far enough, what letting go does. */
@Composable
private fun SwipeHint(locked: Boolean, swiped: Boolean, base: Float) {
    val (icon, text) = when {
        !locked && !swiped -> Icons.Filled.KeyboardArrowDown to "下滑锁定 2 倍速"
        !locked -> Icons.Filled.Lock to "松手锁定 2 倍速"
        !swiped -> Icons.Filled.KeyboardArrowUp to "上滑取消 2 倍速"
        else -> Icons.AutoMirrored.Filled.Undo to "松手" + restoring(base)
    }
    val bounce by rememberInfiniteTransition(label = "bounce").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "bounce",
    )
    val fg = if (swiped) Color.Black else Color.White
    Row(
        Modifier.clip(CircleShape).background(if (swiped) Color.White else Color.Black.copy(alpha = 0.45f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dy = if (swiped) 0f else (if (locked) -3f else 3f) * bounce
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp).offset(y = dy.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = fg, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

// MARK: - Under and beside the video

/** EpisodeBar opens the episodes, under the video on phones. */
@Composable
private fun EpisodeBar(open: () -> Unit) {
    val total = LocalModel.current.player.page?.episodes?.size ?: 0
    Row(
        Modifier.navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
            .fillMaxWidth().height(44.dp).clip(MaterialTheme.shapes.medium).background(Color(0xFF262626))
            .clickable(onClick = open).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.GridView, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (total > 0) "选集 · 全 $total 集" else "选集", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = Color.White)
    }
}

private fun episodeLine(player: Player): String {
    val total = player.page?.episodes?.size ?: 0
    return if (total > 0) "第 ${player.number} 集 / 共 $total 集" else "第 ${player.number} 集"
}

/**
 * EpisodePanel is the series' title and its episodes, beside the video on
 * tablets, with buttons to move between them, and in a sheet on phones,
 * which `picked` closes once something plays.
 */
@Composable
private fun EpisodePanel(modifier: Modifier = Modifier, steps: Boolean = false, picked: () -> Unit = {}) {
    val model = LocalModel.current
    val player = model.player
    val sp = player.page
    val series = sp?.series
    if (sp == null || series == null) {
        Spinner(modifier)
        return
    }
    val watched = model.history.get(sp.id)?.watched?.toSet() ?: emptySet()
    Column(modifier) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(series.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(episodeLine(player), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (steps) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { player.step(-1) }, enabled = player.hasPrevious, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("上一集")
                    }
                    OutlinedButton(onClick = { player.step(1) }, enabled = player.hasNext, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Icon(Icons.Filled.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("下一集")
                    }
                }
            }
            SeasonChips(sp, 20.dp, Modifier.bleed(20.dp)) { se ->
                model.playSeason(se.seriesId)
                picked()
            }
            PartPicker(sp, wide = false)
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
        val grid = rememberLazyGridState()
        val episodes = sp.partEpisodes
        LaunchedEffect(player.number, sp.part) {
            val i = episodes.indexOfFirst { it.number == player.number }
            if (i < 0) return@LaunchedEffect
            grid.scrollToItem(i)
            grid.scrollBy(-(grid.layoutInfo.viewportSize.height / 2f) + 60f)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(46.dp),
            state = grid,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(episodes, key = { it.id }) { e ->
                EpisodeChip(e, e.number == player.number, e.number in watched) {
                    if (e.number != player.number) player.play(sp.id, e.number)
                    picked()
                }
            }
        }
    }
}

// MARK: - Searching from the player

/**
 * PlayerSearch looks for another series without leaving the player: the
 * hot searches, then what was searched for. Picking a series plays it; the
 * back button beside the field goes back to the episode.
 */
@Composable
private fun PlayerSearch(close: (Card?) -> Unit) {
    val model = LocalModel.current
    val focusManager = LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(text) {
        if (text.isEmpty()) query = ""
        if (text.isEmpty() || text == query) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        suggestions = model.suggestions(text)
    }
    fun submit(s: String) {
        val q = s.trim()
        if (q.isEmpty()) return
        text = q
        query = q
        suggestions = emptyList()
        focusManager.clearFocus()
    }
    CompositionLocalProvider(LocalPlayCard provides { card: Card -> close(card) }) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.systemBars).imePadding()) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { close(null) }) { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "返回播放") }
                SearchField(text, { text = it }, { submit(text) }, Modifier.weight(1f), focus)
                TextButton(onClick = { submit(text) }) { Text("搜索", fontWeight = FontWeight.SemiBold) }
            }
            Box(Modifier.weight(1f)) {
                when {
                    suggestions.isNotEmpty() && text != query -> SuggestionList(suggestions, ::submit)
                    query.isEmpty() -> HotView()
                    else -> {
                        val feed = model.searchFeed(query)
                        FeedKey(feed) { FeedGrid(feed, "没有找到“$query”相关的短剧，换个词试试") }
                    }
                }
            }
        }
    }
}
