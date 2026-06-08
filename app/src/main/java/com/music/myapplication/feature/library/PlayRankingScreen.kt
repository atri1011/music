package com.music.myapplication.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.PlaybackEvent
import com.music.myapplication.domain.repository.RecentPlay
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.feature.components.EmptyStateView
import com.music.myapplication.feature.components.SegmentedChoiceRow
import com.music.myapplication.feature.player.PlayerViewModel
import com.music.myapplication.ui.theme.AppShapes
import com.music.myapplication.ui.theme.QQMusicGreen
import com.music.myapplication.ui.theme.appPremiumBackground
import com.music.myapplication.ui.theme.glassSurface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

private val RankGold = Color(0xFFFFD700)
private val RankSilver = Color(0xFF8EA7C7)
private val RankBronze = Color(0xFFC8843F)
private val ArtistAccentBlue = Color(0xFF5B8DEF)
private val ArtistAccentPink = Color(0xFFFF6B8A)
private val ArtistAccentOrange = Color(0xFFFFA24C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayRankingScreen(
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit = {},
    playerViewModel: PlayerViewModel,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rankedTracks = state.topPlayedTracks
    var selectedPeriod by remember { mutableStateOf(ListeningStatsPeriod.Day) }
    val dashboardStats = remember(
        state.playbackEvents,
        state.recentPlayEntries,
        state.topPlayedTracks,
        state.totalPlayCount,
        state.totalListenDurationMs,
        selectedPeriod
    ) {
        buildListeningDashboardStats(
            playbackEvents = state.playbackEvents,
            recentEntries = state.recentPlayEntries,
            topPlayedTracks = state.topPlayedTracks,
            totalPlayCount = state.totalPlayCount,
            totalListenDurationMs = state.totalListenDurationMs,
            period = selectedPeriod
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("播放统计") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .appPremiumBackground()
                .padding(padding)
        ) {
            if (!dashboardStats.hasData && rankedTracks.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Filled.PlayArrow,
                    title = "还没有播放记录",
                    subtitle = "等你多放几首歌，这里就会把播放次数、常听歌手和活跃时段排得明明白白。",
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
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    item {
                        ListeningStatsDashboard(
                            stats = dashboardStats,
                            selectedPeriod = selectedPeriod,
                            onPeriodSelected = { selectedPeriod = it }
                        )
                    }

                    item {
                        RankingSectionHeader(count = rankedTracks.size)
                    }

                    itemsIndexed(
                        items = rankedTracks,
                        key = { _, pair -> "rank:${pair.first.platform.id}:${pair.first.id}" }
                    ) { index, (track, playCount) ->
                        RankedTrackItem(
                            rank = index + 1,
                            track = track,
                            playCount = playCount,
                            onClick = {
                                val tracks = rankedTracks.map { it.first }
                                playerViewModel.playTrack(track, tracks, index)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListeningStatsDashboard(
    stats: ListeningDashboardStats,
    selectedPeriod: ListeningStatsPeriod,
    onPeriodSelected: (ListeningStatsPeriod) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "本地统计",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
        )
        Text(
            text = stats.sourceLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ListeningSummaryGrid(stats = stats)

        SegmentedChoiceRow(
            items = ListeningStatsPeriod.entries,
            selectedItem = selectedPeriod,
            onItemSelected = onPeriodSelected
        ) { period, selected ->
            Text(
                text = period.label,
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }

        DashboardPanel(title = "播放时长趋势") {
            ListeningTrendChart(points = stats.trendPoints)
        }

        DashboardPanel(title = "常听歌手") {
            TopArtistChart(artists = stats.topArtists)
        }

        DashboardPanel(title = "活跃时段") {
            ActiveHourHeatmap(hours = stats.activeHours)
        }
    }
}

@Composable
private fun ListeningSummaryGrid(stats: ListeningDashboardStats) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryTile(
                label = "聆听时长",
                value = formatLongDuration(stats.totalListenDurationMs),
                modifier = Modifier.weight(1f)
            )
            SummaryTile(
                label = "播放次数",
                value = stats.totalPlayCount.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryTile(
                label = "常听时段",
                value = stats.mostActiveHourLabel,
                modifier = Modifier.weight(1f)
            )
            SummaryTile(
                label = "平均单曲",
                value = formatLongDuration(stats.averageListenDurationMs),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .glassSurface(shape = RoundedCornerShape(AppShapes.Medium))
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
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
private fun DashboardPanel(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape = RoundedCornerShape(AppShapes.Large))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun ListeningTrendChart(points: List<ListeningTrendPoint>) {
    val lineColor = QQMusicGreen
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    if (points.isEmpty() || points.all { it.listenDurationMs <= 0L }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无趋势数据",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        }
        return
    }

    val maxDuration = points.maxOf { it.listenDurationMs }.coerceAtLeast(1L)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
        ) {
            val chartLeft = 4.dp.toPx()
            val chartRight = size.width - 4.dp.toPx()
            val chartTop = 8.dp.toPx()
            val chartBottom = size.height - 12.dp.toPx()
            val chartWidth = chartRight - chartLeft
            val chartHeight = chartBottom - chartTop
            val xStep = if (points.size > 1) chartWidth / (points.size - 1) else 0f
            val coordinates = points.mapIndexed { index, point ->
                val x = chartLeft + xStep * index
                val fraction = point.listenDurationMs.toFloat() / maxDuration.toFloat()
                val y = chartBottom - chartHeight * fraction.coerceIn(0f, 1f)
                Offset(x, y)
            }

            repeat(4) { row ->
                val y = chartTop + chartHeight * row / 3f
                drawLine(
                    color = gridColor,
                    start = Offset(chartLeft, y),
                    end = Offset(chartRight, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val areaPath = Path().apply {
                moveTo(coordinates.first().x, chartBottom)
                coordinates.forEach { lineTo(it.x, it.y) }
                lineTo(coordinates.last().x, chartBottom)
                close()
            }
            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.30f),
                        lineColor.copy(alpha = 0.05f)
                    ),
                    startY = chartTop,
                    endY = chartBottom
                )
            )

            val linePath = Path().apply {
                moveTo(coordinates.first().x, coordinates.first().y)
                coordinates.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            coordinates.forEach { point ->
                drawCircle(color = lineColor, radius = 3.5.dp.toPx(), center = point)
                drawCircle(color = Color.White.copy(alpha = 0.75f), radius = 1.5.dp.toPx(), center = point)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEach { point ->
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TopArtistChart(artists: List<ArtistStat>) {
    val palette = remember {
        listOf(QQMusicGreen, ArtistAccentBlue, ArtistAccentPink, ArtistAccentOrange, RankSilver)
    }
    val totalDuration = artists.sumOf { it.listenDurationMs }.coerceAtLeast(1L)

    if (artists.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无歌手统计",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Canvas(modifier = Modifier.size(108.dp)) {
            var startAngle = -90f
            artists.take(5).forEachIndexed { index, artist ->
                val sweep = 360f * artist.listenDurationMs.toFloat() / totalDuration.toFloat()
                drawArc(
                    color = palette[index % palette.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Butt),
                    size = Size(size.width, size.height)
                )
                startAngle += sweep
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            artists.take(5).forEachIndexed { index, artist ->
                ArtistLegendRow(
                    artist = artist,
                    color = palette[index % palette.size],
                    totalDuration = totalDuration
                )
            }
        }
    }
}

@Composable
private fun ArtistLegendRow(
    artist: ArtistStat,
    color: Color,
    totalDuration: Long
) {
    val percent = (artist.listenDurationMs * 100 / totalDuration).coerceIn(0L, 100L)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActiveHourHeatmap(hours: List<HourStat>) {
    val maxCount = hours.maxOfOrNull { it.playCount }?.coerceAtLeast(1) ?: 1
    val empty = hours.all { it.playCount <= 0 }
    val baseColor = QQMusicGreen
    val quietColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
        ) {
            val columns = 12
            val rows = 2
            val gap = 5.dp.toPx()
            val cellWidth = (size.width - gap * (columns - 1)) / columns
            val cellHeight = (size.height - gap * (rows - 1)) / rows
            hours.forEachIndexed { index, hour ->
                val row = index / columns
                val column = index % columns
                val intensity = if (maxCount > 0) hour.playCount.toFloat() / maxCount.toFloat() else 0f
                val color = if (hour.playCount > 0) {
                    baseColor.copy(alpha = 0.18f + intensity.coerceIn(0f, 1f) * 0.72f)
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
                    cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx())
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("0点", "6点", "12点", "18点", "23点").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor
                )
            }
        }
        Text(
            text = if (empty) "暂无明显活跃时段" else "颜色越亮，代表该小时段播放越集中",
            style = MaterialTheme.typography.labelSmall,
            color = labelColor
        )
    }
}

@Composable
private fun RankingSectionHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(QQMusicGreen)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "播放排行",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Top $count",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = QQMusicGreen
        )
    }
}

@Composable
private fun RankedTrackItem(
    rank: Int,
    track: Track,
    playCount: Int,
    onClick: () -> Unit
) {
    val isTopThree = rank <= 3
    val rankColor = when (rank) {
        1 -> RankGold
        2 -> RankBronze
        3 -> RankSilver
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val rankBrush = when (rank) {
        1 -> Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFD4AF37)))
        2 -> Brush.linearGradient(listOf(Color(0xFFF2C389), Color(0xFFC8843F)))
        3 -> Brush.linearGradient(listOf(Color(0xFFE8F0FB), Color(0xFF8EA7C7)))
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(AppShapes.Small))
            .then(
                if (isTopThree) Modifier.background(rankColor.copy(alpha = 0.06f))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (isTopThree) 8.dp else 0.dp,
                vertical = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .then(
                    if (isTopThree) Modifier
                        .clip(RoundedCornerShape(AppShapes.ExtraSmall))
                        .background(rankColor.copy(alpha = 0.15f))
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$rank",
                style = if (rankBrush != null) {
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        brush = rankBrush
                    )
                } else {
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = rankColor
                    )
                }
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        CoverImage(
            url = track.coverUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(if (isTopThree) 52.dp else 48.dp)
                .clip(RoundedCornerShape(if (isTopThree) 10.dp else AppShapes.ExtraSmall))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$playCount 次",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (isTopThree) rankColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = track.platform.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

private enum class ListeningStatsPeriod(val label: String, val bucketCount: Int, val bucketMillis: Long) {
    Day("近7日", 7, DAY_MS),
    Week("近4周", 4, WEEK_MS),
    Month("近6月", 6, MONTH_MS)
}

private data class ListeningDashboardStats(
    val sourceLabel: String,
    val totalPlayCount: Int,
    val totalListenDurationMs: Long,
    val averageListenDurationMs: Long,
    val mostActiveHourLabel: String,
    val trendPoints: List<ListeningTrendPoint>,
    val topArtists: List<ArtistStat>,
    val activeHours: List<HourStat>,
    val hasData: Boolean
)

private data class DashboardPlaybackEvent(
    val track: Track,
    val playedAt: Long,
    val listenDurationMs: Long,
    val playCount: Int
)

private data class ListeningTrendPoint(
    val label: String,
    val listenDurationMs: Long,
    val playCount: Int
)

private data class ArtistStat(
    val name: String,
    val listenDurationMs: Long,
    val playCount: Int
)

private data class HourStat(
    val hour: Int,
    val listenDurationMs: Long,
    val playCount: Int
)

private fun buildListeningDashboardStats(
    playbackEvents: List<PlaybackEvent>,
    recentEntries: List<RecentPlay>,
    topPlayedTracks: List<Pair<Track, Int>>,
    totalPlayCount: Int,
    totalListenDurationMs: Long,
    period: ListeningStatsPeriod
): ListeningDashboardStats {
    val events = when {
        playbackEvents.isNotEmpty() -> playbackEvents.map {
            DashboardPlaybackEvent(
                track = it.track,
                playedAt = it.playedAt,
                listenDurationMs = effectiveListenDurationMs(
                    durationMs = it.listenDurationMs,
                    fallbackDurationMs = it.track.durationMs,
                    playCount = it.playCount
                ),
                playCount = it.playCount.coerceAtLeast(1)
            )
        }
        recentEntries.isNotEmpty() -> recentEntries.map {
            DashboardPlaybackEvent(
                track = it.track,
                playedAt = it.playedAt,
                listenDurationMs = effectiveListenDurationMs(
                    durationMs = 0L,
                    fallbackDurationMs = it.track.durationMs,
                    playCount = it.playCount
                ),
                playCount = it.playCount.coerceAtLeast(1)
            )
        }
        else -> emptyList()
    }

    val sourceLabel = if (playbackEvents.isNotEmpty()) {
        "基于本地播放事件，持续播放后趋势会更细。"
    } else {
        "基于最近播放聚合记录；后续播放会自动沉淀更细的趋势。"
    }
    val eventPlayCount = events.sumOf { it.playCount }
    val resolvedPlayCount = eventPlayCount.takeIf { it > 0 } ?: totalPlayCount
    val eventDuration = events.sumOf { it.listenDurationMs }
    val resolvedDuration = eventDuration.takeIf { it > 0L } ?: totalListenDurationMs
    val averageDuration = if (resolvedPlayCount > 0) resolvedDuration / resolvedPlayCount else 0L
    val activeHours = buildHourStats(events)
    val mostActiveHour = activeHours.maxByOrNull { it.playCount }?.takeIf { it.playCount > 0 }
    val artists = buildArtistStats(events).ifEmpty { buildArtistStatsFromRanking(topPlayedTracks) }

    return ListeningDashboardStats(
        sourceLabel = sourceLabel,
        totalPlayCount = resolvedPlayCount,
        totalListenDurationMs = resolvedDuration,
        averageListenDurationMs = averageDuration,
        mostActiveHourLabel = mostActiveHour?.let { "${it.hour}点" } ?: "暂无",
        trendPoints = buildTrendPoints(events, period),
        topArtists = artists,
        activeHours = activeHours,
        hasData = events.isNotEmpty() || topPlayedTracks.isNotEmpty() || totalPlayCount > 0
    )
}

private fun buildTrendPoints(
    events: List<DashboardPlaybackEvent>,
    period: ListeningStatsPeriod
): List<ListeningTrendPoint> {
    if (period.bucketCount <= 0) return emptyList()
    val now = System.currentTimeMillis()
    val firstStart = now - period.bucketMillis * (period.bucketCount - 1)
    return (0 until period.bucketCount).map { index ->
        val start = firstStart + index * period.bucketMillis
        val end = start + period.bucketMillis
        val bucketEvents = events.filter { it.playedAt in start until end }
        ListeningTrendPoint(
            label = start.shortDateLabel(),
            listenDurationMs = bucketEvents.sumOf { it.listenDurationMs },
            playCount = bucketEvents.sumOf { it.playCount }
        )
    }
}

private fun buildArtistStats(events: List<DashboardPlaybackEvent>): List<ArtistStat> =
    events.groupBy { it.track.artist.ifBlank { "未知歌手" } }
        .map { (artist, plays) ->
            ArtistStat(
                name = artist,
                listenDurationMs = plays.sumOf { it.listenDurationMs },
                playCount = plays.sumOf { it.playCount }
            )
        }
        .sortedWith(compareByDescending<ArtistStat> { it.listenDurationMs }.thenByDescending { it.playCount })
        .take(5)

private fun buildArtistStatsFromRanking(topPlayedTracks: List<Pair<Track, Int>>): List<ArtistStat> =
    topPlayedTracks.groupBy { it.first.artist.ifBlank { "未知歌手" } }
        .map { (artist, entries) ->
            val playCount = entries.sumOf { it.second }
            val duration = entries.sumOf { (track, count) -> track.durationMs.coerceAtLeast(0L) * count.coerceAtLeast(1) }
            ArtistStat(name = artist, listenDurationMs = duration, playCount = playCount)
        }
        .sortedWith(compareByDescending<ArtistStat> { it.listenDurationMs }.thenByDescending { it.playCount })
        .take(5)

private fun buildHourStats(events: List<DashboardPlaybackEvent>): List<HourStat> {
    val grouped = events.groupBy { it.playedAt.hourOfDay() }
    return (0..23).map { hour ->
        val hourEvents = grouped[hour].orEmpty()
        HourStat(
            hour = hour,
            listenDurationMs = hourEvents.sumOf { it.listenDurationMs },
            playCount = hourEvents.sumOf { it.playCount }
        )
    }
}

private fun effectiveListenDurationMs(
    durationMs: Long,
    fallbackDurationMs: Long,
    playCount: Int
): Long {
    if (durationMs > 0L) return durationMs
    return fallbackDurationMs.coerceAtLeast(0L) * playCount.coerceAtLeast(1)
}

private fun Long.hourOfDay(): Int = Calendar.getInstance().apply { timeInMillis = this@hourOfDay }
    .get(Calendar.HOUR_OF_DAY)

private fun Long.shortDateLabel(): String = SimpleDateFormat("M/d", Locale.getDefault()).format(Date(this))

private fun formatLongDuration(ms: Long): String {
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

private const val DAY_MS = 24L * 60L * 60L * 1000L
private const val WEEK_MS = 7L * DAY_MS
private const val MONTH_MS = 30L * DAY_MS
