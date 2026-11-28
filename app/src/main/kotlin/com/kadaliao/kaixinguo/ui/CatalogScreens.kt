@file:OptIn(ExperimentalMaterial3Api::class)

package com.kadaliao.kaixinguo.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kadaliao.kaixinguo.model.Card
import com.kadaliao.kaixinguo.model.Watch
import com.kadaliao.kaixinguo.model.dayLabel
import com.kadaliao.kaixinguo.model.friendly
import com.kadaliao.kaixinguo.model.todayKey
import com.kadaliao.kaixinguo.MainActivity
import com.kadaliao.kaixinguo.cast.PhoneDiagnosticReport
import com.kadaliao.kaixinguo.cast.TvHandoffResultRecord
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** DiscoverScreen is the recommended series. */
@Composable
fun DiscoverScreen() {
    val model = LocalModel.current
    Page("推荐") {
        FeedGrid(model.home, "暂时没有推荐", refresh = { model.home.refresh(merging = true) })
    }
}

/** RankScreen is the charts: a kind of series, and its boards. */
@Composable
fun RankScreen() {
    val model = LocalModel.current
    Page("排行榜") {
        val feed = model.rankFeed
        FeedKey(feed) {
            FeedGrid(feed, "这个榜单暂时是空的", refresh = { feed.refresh(merging = false) }) { RankControls() }
        }
    }
}

@Composable
private fun RankControls() {
    val model = LocalModel.current
    val contents = model.chartContents
    if (contents.isEmpty()) return
    Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (contents.size > 1) {
            val current = model.currentRankContent
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                contents.forEachIndexed { i, c ->
                    SegmentedButton(
                        selected = c.id == current,
                        onClick = {
                            model.rankContent = c.id
                            model.rankBoard = ""
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, contents.size),
                        icon = {},
                    ) { Text(c.name, maxLines = 1) }
                }
            }
        }
        val boards = model.rankBoards
        ChipRow(selected = boards.indexOfFirst(model::boardIsOn)) {
            items(boards, key = { it.id }) { b ->
                Chip(b.name, model.boardIsOn(b)) { model.rankBoard = b.id }
            }
        }
    }
}

/** NewReleasesScreen is the calendar of new series, a week either side. */
@Composable
fun NewReleasesScreen() {
    val model = LocalModel.current
    Page("新剧上线") {
        val feed = model.newFeed
        FeedKey(feed) {
            FeedGrid(feed, "这一天没有新剧", refresh = { feed.refresh(merging = false) }) {
                val dates = model.newDates
                if (dates.isNotEmpty()) {
                    val today = todayKey()
                    ChipRow(selected = dates.indexOfFirst(model::dayIsOn), modifier = Modifier.padding(bottom = 4.dp)) {
                        items(dates, key = { it }) { d ->
                            Chip(dayLabel(d, today), model.dayIsOn(d)) { model.chooseDay(d) }
                        }
                    }
                }
            }
        }
    }
}

/** SearchScreen searches the series; before a search, it shows what people search for. */
@Composable
fun SearchScreen() {
    val model = LocalModel.current
    val router = LocalRouter.current
    val focusManager = LocalFocusManager.current
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var focused by remember { mutableStateOf(false) }
    val query = router.searchQuery
    LaunchedEffect(router.searchText) {
        val text = router.searchText
        if (text.isEmpty()) router.searchQuery = ""
        if (text.isEmpty() || text == router.searchQuery) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        suggestions = model.suggestions(text)
    }
    fun submit(s: String) {
        router.search(s)
        suggestions = emptyList()
        focusManager.clearFocus()
    }
    Scaffold(
        topBar = {
            Row(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                SearchField(
                    router.searchText,
                    { router.searchText = it },
                    { submit(router.searchText) },
                    Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                )
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                suggestions.isNotEmpty() && router.searchText != query && focused -> SuggestionList(suggestions, ::submit)
                query.isEmpty() -> HotView()
                else -> {
                    val feed = model.searchFeed(query)
                    FeedKey(feed) { FeedGrid(feed, "没有找到“$query”相关的短剧，换个词试试", retryLabel = "重新搜索") }
                }
            }
        }
    }
}

/** HotView is the hot-search boards. */
@Composable
fun HotView() {
    val model = LocalModel.current
    LaunchedEffect(Unit) { model.loadHot() }
    val hot = model.hot
    val error = model.hotError
    when {
        hot != null -> Refreshable({ model.reloadHot() }) {
            CardGrid {
                hot.boards.filter { it.items.isNotEmpty() }.forEachIndexed { bi, b ->
                    line("board$bi") {
                        Text(b.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                    }
                    itemsIndexed(b.items, key = { i, _ -> "$bi/$i" }) { _, it ->
                        CardView(
                            Card(
                                it.seriesId, it.title, it.coverUrl,
                                sub = it.searchScore.ifEmpty { it.heat },
                                rank = it.position, episodes = it.episodeCount,
                            ),
                        )
                    }
                }
            }
        }
        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ErrorState(friendly(error)) { model.loadHot(force = true) }
        }
        else -> Spinner()
    }
}

/** MineScreen is what the viewer watched and saved. */
@Composable
fun MineScreen() {
    val model = LocalModel.current
    val context = LocalContext.current
    val activity = context as? MainActivity
    val watched = model.history.list.take(30)
    val saved = model.favorites.cards
    var menuExpanded by remember { mutableStateOf(false) }
    var receivingDiagnostics by remember { mutableStateOf(activity?.isDiagnosticReceiverEnabled() == true) }
    var inboxOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var inbox by remember { mutableStateOf(emptyList<PhoneDiagnosticReport>()) }
    val appPackageInfo = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val appVersionName = appPackageInfo?.versionName ?: "未知"
    val appVersionCode = appPackageInfo?.longVersionCode?.toString() ?: "未知"
    var lastTvHandoff by remember { mutableStateOf(TvHandoffResultRecord.load(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            lastTvHandoff = TvHandoffResultRecord.load(context)
            delay(1_200)
        }
    }

    Page(
        title = "我的",
        actions = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("立即更新") },
                        onClick = {
                            menuExpanded = false
                            activity?.checkForUpdatesManually()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("接收诊断", modifier = Modifier.weight(1f))
                                Switch(checked = receivingDiagnostics, onCheckedChange = null)
                            }
                        },
                        onClick = {
                            receivingDiagnostics = !receivingDiagnostics
                            activity?.setDiagnosticReceiverEnabled(receivingDiagnostics)
                            menuExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("诊断收件箱") },
                        onClick = {
                            menuExpanded = false
                            inbox = activity?.diagnosticInboxReports().orEmpty()
                            inboxOpen = true
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("关于开心果")
                                Text("TZ 生日特别版", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        onClick = {
                            menuExpanded = false
                            aboutOpen = true
                        },
                    )
                }
            }
        },
    ) {
        CardGrid {
            lastTvHandoff?.let { result ->
                line("tv-handoff-status") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(
                            "最近一次电视接力",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            when {
                                result.success -> "投屏成功 · 电视已确认开始播放"
                                result.outcomeUnknown -> "接力状态未知 · 尚未收到电视确认"
                                else -> "投屏未成功"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                result.success -> MaterialTheme.colorScheme.primary
                                result.outcomeUnknown -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.error
                            },
                        )
                        Text("${result.deviceName} · 第 ${result.episode} 集", style = MaterialTheme.typography.bodyMedium)
                        if (result.message.isNotBlank()) {
                            Text(result.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(result.completedAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (watched.isNotEmpty()) {
                line("watched-title") { SectionTitle("最近观看") }
                line("watched") {
                    LazyRow(
                        Modifier.bleed(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(watched, key = { it.seriesID }) { WatchCard(it) }
                    }
                }
            }
            line("saved-title") { SectionTitle("收藏") }
            if (saved.isEmpty()) {
                line("saved-empty") {
                    EmptyState(
                        Icons.Outlined.StarOutline,
                        "还没有收藏",
                        "在剧集页点「收藏」，或者长按海报，想看的就存下来。",
                        Modifier.padding(top = 24.dp),
                    )
                }
            } else {
                items(saved, key = { it.id }) { CardView(it) }
            }
        }
    }

    if (inboxOpen) {
        AlertDialog(
            onDismissRequest = { inboxOpen = false },
            title = { Text("诊断收件箱") },
            text = {
                if (inbox.isEmpty()) {
                    Text("还没有收到电视发送的诊断信息。请先开启“接收诊断”，并让手机与电视连接同一局域网。")
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(inbox, key = { it.reportId }) { report ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(report.sourceName, fontWeight = FontWeight.SemiBold)
                                Text("${report.receivedAt} · ${report.sourceVersion}", style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("hongguoTV 播放诊断", report.report))
                                    android.widget.Toast.makeText(context, "完整诊断已复制", android.widget.Toast.LENGTH_SHORT).show()
                                }) { Text("复制完整诊断") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { inboxOpen = false }) { Text("关闭") } },
        )
    }
    if (aboutOpen) {
        AlertDialog(
            onDismissRequest = { aboutOpen = false },
            title = { Text("开心果") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TZ 生日特别版", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("2026年11月28日", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("愿快乐常伴，好剧常在。", style = MaterialTheme.typography.bodyLarge)
                    Text("A Little Gift for TZ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text("版本：$appVersionName（versionCode $appVersionCode）", style = MaterialTheme.typography.bodySmall)
                    Text("开发者：by Edison", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { aboutOpen = false }) { Text("关闭") } },
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

/** WatchCard is a series in the history, how far the viewer got, and a way to go on. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WatchCard(watch: Watch) {
    val model = LocalModel.current
    val router = LocalRouter.current
    val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .width(96.dp)
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(
                    onClick = { router.open(Route.Series(watch.seriesID)) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menu = true
                    },
                ),
        ) {
            Poster(watch.cover) {
                if (!watch.finished && watch.duration > 0) {
                    val f = (watch.position / watch.duration).coerceIn(0.0, 1.0).toFloat()
                    Box(
                        Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, bottom = 7.dp)
                            .fillMaxWidth(f.coerceAtLeast(0.05f)).height(3.dp)
                            .clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(watch.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (watch.finished) "看完第 ${watch.episode} 集" else "看到第 ${watch.episode} 集",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("继续播放") },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                onClick = {
                    menu = false
                    model.playSeason(watch.seriesID)
                },
            )
            DropdownMenuItem(
                text = { Text("从最近观看中移除", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    menu = false
                    model.history.remove(watch.seriesID)
                },
            )
        }
    }
}
