package com.music.myapplication.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.myapplication.domain.model.Track
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.feature.components.formatDuration
import com.music.myapplication.ui.theme.AppShapes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SongInfoPage(
    track: Track,
    trackInfoState: TrackInfoUiState,
    playerViewModel: PlayerViewModel,
    onNavigateToAlbum: ((String, String, String, String, String) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val canOpenAlbum = track.albumId.isNotBlank() && onNavigateToAlbum != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = track.title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.6f)
        )
        if (track.album.isNotBlank()) {
            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("所属专辑")
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppShapes.Medium))
                    .background(Color.White.copy(alpha = if (canOpenAlbum) 0.14f else 0.08f))
                    .then(
                        if (canOpenAlbum) {
                            Modifier.clickable {
                                onNavigateToAlbum?.invoke(
                                    track.albumId,
                                    track.platform.id,
                                    track.album,
                                    track.artist,
                                    track.coverUrl
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column {
                    Text(
                        text = track.album,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                    Text(
                        text = if (canOpenAlbum) "点一下进入专辑详情" else "当前来源暂不支持打开专辑详情",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader("音乐百科")
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("曲风: 流行")
            InfoChip("语种: 国语")
            if (track.durationMs > 0) {
                InfoChip("时长: ${formatDuration(track.durationMs)}")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader("回忆坐标")
        Spacer(modifier = Modifier.height(8.dp))
        if (trackInfoState.firstPlayDate != null) {
            val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()) }
            val season = remember(trackInfoState.firstPlayDate) {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = trackInfoState.firstPlayDate }
                when (cal.get(java.util.Calendar.MONTH)) {
                    in 2..4 -> "春天"
                    in 5..7 -> "夏天"
                    in 8..10 -> "秋天"
                    else -> "冬天"
                }
            }
            val timeOfDay = remember(trackInfoState.firstPlayDate) {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = trackInfoState.firstPlayDate }
                when (cal.get(java.util.Calendar.HOUR_OF_DAY)) {
                    in 0..5 -> "凌晨"
                    in 6..11 -> "上午"
                    in 12..13 -> "中午"
                    in 14..17 -> "下午"
                    in 18..20 -> "傍晚"
                    else -> "深夜"
                }
            }
            Text(
                text = "初次播放 ${dateFormat.format(Date(trackInfoState.firstPlayDate))} · $season · $timeOfDay",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f)
            )
        } else {
            Text(
                text = "尚未记录播放历史",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
        Text(
            text = "累计播放 ${trackInfoState.totalPlayCount} 次",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (trackInfoState.similarTracks.isNotEmpty()) {
            SectionHeader("相似歌曲")
            Spacer(modifier = Modifier.height(8.dp))
            trackInfoState.similarTracks.forEach { similarTrack ->
                SimilarTrackItem(
                    track = similarTrack,
                    onClick = {
                        playerViewModel.playTrack(
                            similarTrack,
                            trackInfoState.similarTracks,
                            trackInfoState.similarTracks.indexOf(similarTrack)
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = Color.White
    )
}

@Composable
private fun InfoChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AppShapes.Medium))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun SimilarTrackItem(
    track: Track,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppShapes.Small))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(
            url = track.coverUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(AppShapes.ExtraSmall)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "播放",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
}
