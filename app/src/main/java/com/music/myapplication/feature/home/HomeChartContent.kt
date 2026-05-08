package com.music.myapplication.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.ToplistInfo
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.feature.components.ErrorView
import com.music.myapplication.feature.components.PlatformFilterChips
import com.music.myapplication.feature.components.ShimmerGridCard
import com.music.myapplication.ui.theme.AppShapes
import com.music.myapplication.ui.theme.AppSpacing
import com.music.myapplication.ui.theme.QQMusicGreen
import com.music.myapplication.ui.theme.glassSurface

@Composable
fun ChartContent(
    state: HomeUiState,
    onPlatformChange: (Platform) -> Unit,
    onRetry: () -> Unit,
    onNavigateToPlaylist: (id: String, platform: String, name: String, source: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PlatformFilterChips(
            selectedPlatform = state.platform,
            onPlatformSelected = onPlatformChange,
            modifier = Modifier.padding(horizontal = AppSpacing.Medium, vertical = AppSpacing.XXSmall)
        )

        when {
            state.error != null -> ErrorView(message = state.error!!, onRetry = onRetry)
            state.isLoading -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = AppSpacing.Medium, vertical = AppSpacing.XSmall),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Small),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(4) {
                        ShimmerGridCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(AppShapes.Medium))
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = AppSpacing.Medium, vertical = AppSpacing.XSmall),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Small),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        state.toplists,
                        key = { it.id },
                        contentType = { "toplist_card" }
                    ) { toplist ->
                        ToplistPreviewCard(
                            toplist = toplist,
                            previewTracks = state.toplistPreviews[toplist.id],
                            onClick = {
                                onNavigateToPlaylist(toplist.id, state.platform.id, toplist.name, "toplist")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToplistPreviewCard(
    toplist: ToplistInfo,
    previewTracks: List<Track>?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape = RoundedCornerShape(AppShapes.Large), pressScale = true)
            .clickable(onClick = onClick)
            .padding(AppSpacing.Medium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = toplist.name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(AppSpacing.XSmall))
            Text(
                text = "每天更新",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(AppSpacing.Small))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(AppShapes.Small)),
                contentAlignment = Alignment.Center
            ) {
                CoverImage(
                    url = toplist.coverUrl,
                    contentDescription = toplist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.32f))
                        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(AppSpacing.Small))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.XSmall)
            ) {
                if (previewTracks != null) {
                    previewTracks.forEachIndexed { index, track ->
                        ChartSongRow(rank = index + 1, track = track)
                    }
                } else {
                    repeat(3) { index ->
                        ChartSongRowPlaceholder(rank = index + 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartSongRow(rank: Int, track: Track) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = if (rank <= 3) QQMusicGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )
        Spacer(modifier = Modifier.width(AppSpacing.XSmall))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = " - ${track.artist}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ChartSongRowPlaceholder(rank: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )
        Spacer(modifier = Modifier.width(AppSpacing.XSmall))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(AppShapes.Tiny))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        )
    }
}
