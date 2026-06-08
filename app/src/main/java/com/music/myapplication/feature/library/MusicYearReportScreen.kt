package com.music.myapplication.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.domain.model.Track
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.feature.components.EmptyStateView
import com.music.myapplication.ui.theme.AppShapes
import com.music.myapplication.ui.theme.QQMusicGreen
import com.music.myapplication.ui.theme.appPremiumBackground
import com.music.myapplication.ui.theme.glassSurface
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicYearReportScreen(
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit = {},
    viewModel: MusicYearReportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSharing by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("音乐年度报告") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isSharing = true
                                val error = shareMusicYearReportMessage(context, state)
                                isSharing = false
                                if (error != null) snackbarHostState.showSnackbar(error)
                            }
                        },
                        enabled = state.hasData && !isSharing
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "分享年度报告")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .appPremiumBackground()
                .padding(padding)
        ) {
            if (!state.hasData) {
                EmptyStateView(
                    icon = Icons.Filled.History,
                    title = "还没有年度报告",
                    subtitle = "多听几首歌后，这里会生成本地音乐日历、年度单曲和常听歌手。",
                    modifier = Modifier.fillMaxSize(),
                    action = {
                        FilledTonalButton(onClick = onNavigateToHome) {
                            Text("去发现")
                        }
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        YearReportHero(state = state)
                    }
                    item {
                        YearReportKpiGrid(state = state)
                    }
                    item {
                        YearReportPanel(title = "音乐日历") {
                            YearCalendarHeatmap(
                                year = state.year,
                                days = state.calendarDays,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    item {
                        YearReportPanel(title = "年度单曲") {
                            YearTopTrackList(tracks = state.topTracks)
                        }
                    }
                    item {
                        YearReportPanel(title = "常听歌手") {
                            YearTopArtistList(artists = state.topArtists)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearReportHero(state: MusicYearReportUiState) {
    val topTrack = state.topTracks.firstOrNull()?.track
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .glassSurface(shape = RoundedCornerShape(AppShapes.Large))
            .padding(18.dp)
    ) {
        Text(
            text = "${state.year}",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
        Text(
            text = "本地听歌旅程",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = topTrack?.let { "这一年，你最常回到《${it.title}》。" } ?: "播放越多，报告越完整。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun YearReportKpiGrid(state: MusicYearReportUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            YearReportKpiCard(
                icon = Icons.Outlined.Timer,
                label = "聆听时长",
                value = formatReportDuration(state.totalListenDurationMs),
                modifier = Modifier.weight(1f)
            )
            YearReportKpiCard(
                icon = Icons.Filled.PlayArrow,
                label = "播放次数",
                value = "${state.totalPlayCount} 次",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            YearReportKpiCard(
                icon = Icons.Filled.History,
                label = "活跃天数",
                value = "${state.activeDays} 天",
                modifier = Modifier.weight(1f)
            )
            YearReportKpiCard(
                icon = Icons.Outlined.MusicNote,
                label = "常听时段",
                value = state.activeHour?.let { "${it} 点" } ?: "暂无",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun YearReportKpiCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .glassSurface(shape = RoundedCornerShape(AppShapes.Medium))
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun YearReportPanel(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape = RoundedCornerShape(AppShapes.Large))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(QQMusicGreen)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun YearCalendarHeatmap(
    year: Int,
    days: List<YearReportDayStat>,
    modifier: Modifier = Modifier
) {
    val dayMap = remember(days) { days.associateBy { it.dayOfYear } }
    val maxCount = days.maxOfOrNull { it.playCount }?.coerceAtLeast(1) ?: 1
    val cellColor = QQMusicGreen
    val quietColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dayCount = if (isLeapYear(year)) 366 else 365
    val firstWeekday = remember(year) { firstDayColumnOffset(year) }
    val columns = ((firstWeekday + dayCount + 6) / 7).coerceAtLeast(1)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp)
        ) {
            val rows = 7
            val gap = 4.dp.toPx()
            val cellWidth = (size.width - gap * (columns - 1)) / columns
            val cellHeight = (size.height - gap * (rows - 1)) / rows
            for (day in 1..dayCount) {
                val index = firstWeekday + day - 1
                val column = index / rows
                val row = index % rows
                val stat = dayMap[day]
                val intensity = (stat?.playCount ?: 0).toFloat() / maxCount.toFloat()
                val color = if (stat != null) {
                    cellColor.copy(alpha = 0.18f + intensity.coerceIn(0f, 1f) * 0.72f)
                } else {
                    quietColor
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(
                        x = column * (cellWidth + gap),
                        y = row * (cellHeight + gap)
                    ),
                    size = Size(cellWidth, cellHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            monthLabels().forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor
                )
            }
        }
        Text(
            text = "亮色代表当天播放更集中，今年共 ${days.size} 天留下记录。",
            style = MaterialTheme.typography.labelSmall,
            color = labelColor
        )
    }
}

@Composable
private fun YearTopTrackList(tracks: List<YearReportTrackStat>) {
    if (tracks.isEmpty()) {
        Text(
            text = "暂无年度单曲数据",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tracks.forEachIndexed { index, stat ->
            YearTopTrackRow(rank = index + 1, stat = stat)
        }
    }
}

@Composable
private fun YearTopTrackRow(
    rank: Int,
    stat: YearReportTrackStat
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppShapes.Small))
            .background(
                if (rank == 1) QQMusicGreen.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = if (rank == 1) QQMusicGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(26.dp)
        )
        CoverImage(
            url = stat.track.coverUrl,
            contentDescription = stat.track.title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(AppShapes.ExtraSmall))
        )
        Spacer(modifier = Modifier.width(12.dp))
        TrackStatText(
            track = stat.track,
            listenDurationMs = stat.listenDurationMs,
            playCount = stat.playCount,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TrackStatText(
    track: Track,
    listenDurationMs: Long,
    playCount: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist.ifBlank { "未知歌手" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "$playCount 次 · ${formatReportDuration(listenDurationMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun YearTopArtistList(artists: List<YearReportArtistStat>) {
    if (artists.isEmpty()) {
        Text(
            text = "暂无歌手统计数据",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val maxPlayCount = artists.maxOf { it.playCount }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        artists.forEachIndexed { index, artist ->
            YearTopArtistRow(
                rank = index + 1,
                artist = artist,
                progress = artist.playCount.toFloat() / maxPlayCount.toFloat()
            )
        }
    }
}

@Composable
private fun YearTopArtistRow(
    rank: Int,
    artist: YearReportArtistStat,
    progress: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(QQMusicGreen.copy(alpha = if (rank == 1) 0.18f else 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = QQMusicGreen,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${artist.playCount} 次",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = QQMusicGreen,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f)
            )
        }
    }
}

private fun formatReportDuration(ms: Long): String {
    val totalMinutes = max(0L, ms / 60_000L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours >= 100 -> "${hours}小时"
        hours > 0 -> "${hours}小时${minutes}分"
        minutes > 0 -> "${minutes}分钟"
        ms > 0L -> "<1分钟"
        else -> "0分钟"
    }
}

private fun firstDayColumnOffset(year: Int): Int {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, Calendar.JANUARY)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
}

private fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

private fun monthLabels(): List<String> {
    val shortMonths = DateFormatSymbols.getInstance(Locale.getDefault()).shortMonths
    return listOf(0, 2, 5, 8, 11).map { index -> shortMonths[index].trim('.') }
}
