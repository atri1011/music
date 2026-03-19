package com.music.myapplication.feature.library

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.domain.model.Track
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.feature.components.EmptyStateView
import com.music.myapplication.feature.player.PlayerViewModel
import com.music.myapplication.ui.theme.AppShapes
import com.music.myapplication.ui.theme.QQMusicGreen

private val RankGold = Color(0xFFFFD700)
private val RankSilver = Color(0xFF8EA7C7)
private val RankBronze = Color(0xFFC8843F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayRankingScreen(
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rankedTracks = state.topPlayedTracks

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播放次数") },
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
        if (rankedTracks.isEmpty()) {
            EmptyStateView(
                icon = Icons.Filled.PlayArrow,
                title = "还没有播放记录",
                subtitle = "等你多放几首歌，这里就会按播放次数给你排得明明白白。",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
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

@Composable
private fun RankingSectionHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
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
