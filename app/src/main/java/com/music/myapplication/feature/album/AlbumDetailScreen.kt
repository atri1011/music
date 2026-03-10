package com.music.myapplication.feature.album

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.feature.components.ErrorView
import com.music.myapplication.feature.components.MediaListItem
import com.music.myapplication.feature.components.ShimmerMediaListItem
import com.music.myapplication.feature.player.PlayerViewModel

@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.isLoading -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                repeat(8) { ShimmerMediaListItem() }
            }
        }

        state.error != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                ErrorView(message = state.error!!, onRetry = viewModel::retry)
            }
        }

        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Back button
                item(key = "topbar") {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }

                // Header: Cover + vinyl | Info
                item(key = "header", contentType = "header") {
                    AlbumHeaderSection(state)
                }

                // Tags
                if (state.tags.isNotEmpty()) {
                    item(key = "tags", contentType = "tags") {
                        AlbumTagsRow(state.tags)
                    }
                }

                // Description
                if (state.description.isNotBlank()) {
                    item(key = "description", contentType = "desc") {
                        ExpandableDescription(state.description)
                    }
                }

                // Action buttons
                item(key = "actions", contentType = "actions") {
                    AlbumActionButtons(
                        trackCount = state.tracks.size,
                        onPlayAll = {
                            if (state.tracks.isNotEmpty()) {
                                playerViewModel.playTrack(
                                    state.tracks.first(),
                                    state.tracks,
                                    0
                                )
                            }
                        }
                    )
                }

                // Song count header
                if (state.tracks.isNotEmpty()) {
                    item(key = "song-header", contentType = "song-header") {
                        Column {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "歌曲${state.tracks.size}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    top = 16.dp,
                                    bottom = 8.dp
                                )
                            )
                        }
                    }
                }

                // Song list
                itemsIndexed(
                    state.tracks,
                    key = { _, t -> "${t.platform.id}:${t.id}" },
                    contentType = { _, _ -> "track" }
                ) { index, track ->
                    MediaListItem(
                        track = track,
                        index = index,
                        onClick = {
                            playerViewModel.playTrack(track, state.tracks, index)
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun AlbumHeaderSection(state: AlbumDetailUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Cover with vinyl disc
        AlbumCoverWithVinyl(
            coverUrl = state.coverUrl,
            albumName = state.albumName,
            modifier = Modifier.weight(0.5f)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Album info (right side)
        Column(
            modifier = Modifier.weight(0.5f),
            horizontalAlignment = Alignment.End
        ) {
            // Artist name
            if (state.artistName.isNotBlank()) {
                Text(
                    text = state.artistName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Album name
            Text(
                text = state.albumName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Year
            if (state.publishTime.isNotBlank()) {
                Text(
                    text = state.publishTime,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 36.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "发行时间",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Genre · Language · Track count
            val infoLine = buildString {
                if (state.genre.isNotBlank()) append(state.genre)
                if (state.language.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(state.language)
                }
                if (state.tracks.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append("${state.tracks.size}首")
                }
            }
            if (infoLine.isNotBlank()) {
                Text(
                    text = infoLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Company
            if (state.company.isNotBlank()) {
                Text(
                    text = state.company,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun AlbumCoverWithVinyl(
    coverUrl: String,
    albumName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.aspectRatio(1.15f),
        contentAlignment = Alignment.CenterStart
    ) {
        // Vinyl disc (behind cover, peeking from right)
        Box(
            modifier = Modifier
                .fillMaxSize(0.85f)
                .offset(x = 50.dp)
                .align(Alignment.CenterStart)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f

                // Main disc body
                drawCircle(
                    color = Color(0xFF1A1A1A),
                    radius = radius,
                    center = center
                )

                // Grooves
                for (i in 1..5) {
                    val grooveRadius = radius * (0.35f + i * 0.1f)
                    drawCircle(
                        color = Color(0xFF252525),
                        radius = grooveRadius,
                        center = center,
                        style = Stroke(width = 0.8f)
                    )
                }

                // Label area
                drawCircle(
                    color = Color(0xFF333333),
                    radius = radius * 0.18f,
                    center = center
                )

                // Center hole
                drawCircle(
                    color = Color(0xFF1A1A1A),
                    radius = radius * 0.05f,
                    center = center
                )
            }
        }

        // Album cover on top
        CoverImage(
            url = coverUrl,
            contentDescription = albumName,
            modifier = Modifier
                .fillMaxSize(0.85f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .align(Alignment.CenterStart)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlbumTagsRow(tags: List<String>) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tags.forEach { tag ->
            Text(
                text = tag,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ExpandableDescription(description: String) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize()
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis
        )
        if (!expanded && description.length > 80) {
            Text(
                text = "更多",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun AlbumActionButtons(
    trackCount: Int,
    onPlayAll: () -> Unit
) {
    if (trackCount <= 0) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onPlayAll,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "全部播放",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
