package com.music.myapplication.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.domain.repository.RecentPlay
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.feature.components.EmptyStateView
import com.music.myapplication.feature.components.formatDuration
import com.music.myapplication.feature.player.PlayerViewModel
import com.music.myapplication.ui.theme.AppShapes
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayHistoryScreen(
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit = {},
    playerViewModel: PlayerViewModel,
    viewModel: PlayHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val groups = state.entries.groupBy { it.playedAt.daySectionLabel() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播放历史") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (state.entries.isEmpty()) {
            EmptyStateView(
                icon = Icons.Filled.History,
                title = "还没有播放历史",
                subtitle = "开始播放后，这里会按日期帮你把最近听过的歌排好。",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                action = {
                    FilledTonalButton(onClick = onNavigateToHome) {
                        Text("去发现")
                    }
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                groups.forEach { (section, entries) ->
                    item(key = "section:$section", contentType = "section") {
                        HistorySectionHeader(section = section, count = entries.size)
                    }
                    items(
                        count = entries.size,
                        key = { index ->
                            val entry = entries[index]
                            "history:${entry.track.platform.id}:${entry.track.id}:${entry.playedAt}"
                        },
                        contentType = { "track" }
                    ) { index ->
                        val entry = entries[index]
                        HistoryTrackItem(
                            entry = entry,
                            onClick = {
                                val queue = state.entries.map { it.track }
                                val queueIndex = state.entries.indexOf(entry).coerceAtLeast(0)
                                playerViewModel.playTrack(entry.track, queue, queueIndex)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySectionHeader(section: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = section,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$count 首",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HistoryTrackItem(
    entry: RecentPlay,
    onClick: () -> Unit
) {
    val track = entry.track
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(
            url = track.coverUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(AppShapes.ExtraSmall))
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
                text = entry.playedAt.timeLabel(),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${entry.playCount} 次 · ${formatDuration(entry.track.durationMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private fun Long.daySectionLabel(): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = this@daySectionLabel }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayDiff = now.startOfDayMillis() - then.startOfDayMillis()
    return when (dayDiff / DAY_MS) {
        0L -> "今天"
        1L -> "昨天"
        else -> DateFormat.getDateInstance(
            if (sameYear) DateFormat.MEDIUM else DateFormat.LONG,
            Locale.getDefault()
        ).format(Date(this))
    }
}

private fun Long.timeLabel(): String =
    DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(this))

private fun Calendar.startOfDayMillis(): Long = apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private const val DAY_MS = 24L * 60L * 60L * 1000L
