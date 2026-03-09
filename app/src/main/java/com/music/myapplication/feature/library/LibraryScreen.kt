package com.music.myapplication.feature.library

import android.os.Build
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import com.music.myapplication.core.common.normalizeCoverUrl
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.feature.player.PlayerViewModel
import com.music.myapplication.ui.theme.QQMusicGreen
import kotlin.math.abs

@Composable
fun LibraryScreen(
    onNavigateToPlaylist: (id: String, name: String) -> Unit,
    onNavigateToDownloaded: () -> Unit = {},
    onNavigateToLocalMusic: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.importedPlaylist) {
        state.importedPlaylist?.let { destination ->
            onNavigateToPlaylist(destination.playlistId, destination.playlistName)
            viewModel.consumeImportedPlaylist()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Layer 1: Blurred cover background (top portion, fades downward)
        val topCoverUrl = state.topPlayedTracks.firstOrNull()?.first?.coverUrl.orEmpty()
        if (topCoverUrl.isNotEmpty()) {
            LibraryBlurredBackground(coverUrl = topCoverUrl)
        }

        // Layer 2: Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Top 5 tilted album covers
            item {
                TopCoversHeader(topTracks = state.topPlayedTracks)
            }

            // Unified stats + favorites panel
            item {
                StatsAndFavoritesPanel(
                    totalPlayCount = state.totalPlayCount,
                    totalListenDurationMs = state.totalListenDurationMs,
                    favoritesCount = state.favorites.size,
                    favoritesCoverUrl = state.favorites.firstOrNull()?.coverUrl.orEmpty(),
                    onFavoritesClick = {
                        if (state.favorites.isNotEmpty()) {
                            playerViewModel.playTrack(
                                state.favorites.first(), state.favorites, 0
                            )
                        }
                    },
                    onImportClick = { viewModel.showImportDialog(true) },
                    onCreateClick = { viewModel.showCreateDialog(true) },
                    onDownloadedClick = onNavigateToDownloaded,
                    downloadedCount = state.downloadedCount,
                    onLocalMusicClick = onNavigateToLocalMusic,
                    localTrackCount = state.localTrackCount
                )
            }

            // User playlists
            val playlists = state.playlists
            if (playlists.isNotEmpty()) {
                items(playlists.size, key = { playlists[it].id }) { index ->
                    val playlist = playlists[index]
                    PlaylistRow(
                        name = playlist.name,
                        coverUrl = playlist.coverUrl,
                        trackCount = playlist.trackCount,
                        onClick = { onNavigateToPlaylist(playlist.id, playlist.name) },
                        onDelete = { viewModel.deletePlaylist(playlist.id) }
                    )
                }
            }

            // Play ranking section
            if (state.topPlayedTracks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "播放排行 Top ${state.topPlayedTracks.size}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                val rankedTracks = state.topPlayedTracks
                itemsIndexed(
                    rankedTracks,
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

        // Dialogs
        if (state.showCreateDialog) {
            CreatePlaylistDialog(
                onDismiss = { viewModel.showCreateDialog(false) },
                onConfirm = viewModel::createPlaylist
            )
        }

        if (state.showImportDialog) {
            ImportPlaylistDialog(
                isImporting = state.isImporting,
                importError = state.importError,
                onDismiss = { viewModel.showImportDialog(false) },
                onConfirm = viewModel::importPlaylist
            )
        }
    }
}

// ── Blurred Cover Background ────────────────────────────────────────────────────

@Composable
private fun LibraryBlurredBackground(coverUrl: String) {
    val context = LocalContext.current
    val normalizedUrl = remember(coverUrl) { normalizeCoverUrl(coverUrl) }
    val useNativeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
    ) {
        val imageRequest = remember(normalizedUrl, useNativeBlur) {
            ImageRequest.Builder(context)
                .data(normalizedUrl.ifEmpty { null })
                .size(if (useNativeBlur) 256 else 32)
                .scale(Scale.FILL)
                .crossfade(800)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
        }

        val painter = rememberAsyncImagePainter(model = imageRequest)

        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(if (useNativeBlur) Modifier.blur(60.dp) else Modifier),
            contentScale = ContentScale.Crop
        )

        // Gradient: semi-transparent at top → fully opaque theme bg at bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            bgColor.copy(alpha = 0.25f),
                            bgColor.copy(alpha = 0.6f),
                            bgColor
                        )
                    )
                )
        )
    }
}

// ── Top 5 Covers ────────────────────────────────────────────────────────────────

@Composable
private fun TopCoversHeader(topTracks: List<Pair<Track, Int>>) {
    if (topTracks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Text(
                    "听些音乐，这里会展示你的 Top 5",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        return
    }

    val tracks = topTracks.take(5)
    val count = tracks.size
    val coverSize = 80.dp
    val spacing = 56.dp

    val angles = when (count) {
        1 -> listOf(0f)
        2 -> listOf(-6f, 6f)
        3 -> listOf(-10f, 0f, 10f)
        4 -> listOf(-12f, -4f, 4f, 12f)
        else -> listOf(-12f, -6f, 0f, 6f, 12f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        val centerIndex = (count - 1) / 2f
        tracks.forEachIndexed { index, (track, _) ->
            val offsetFromCenter = index - centerIndex
            val zIdx = count.toFloat() - abs(index - count / 2).toFloat()

            CoverImage(
                url = track.coverUrl,
                contentDescription = track.title,
                modifier = Modifier
                    .zIndex(zIdx)
                    .offset(x = spacing * offsetFromCenter)
                    .graphicsLayer { rotationZ = angles[index] }
                    .size(coverSize)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}

// ── Stats + Favorites Panel ─────────────────────────────────────────────────────

@Composable
private fun StatsAndFavoritesPanel(
    totalPlayCount: Int,
    totalListenDurationMs: Long,
    favoritesCount: Int,
    favoritesCoverUrl: String,
    onFavoritesClick: () -> Unit,
    onImportClick: () -> Unit,
    onCreateClick: () -> Unit,
    onDownloadedClick: () -> Unit = {},
    downloadedCount: Int = 0,
    onLocalMusicClick: () -> Unit = {},
    localTrackCount: Int = 0
) {
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(surfaceColor.copy(alpha = 0.38f))
    ) {
        // ── Stats row ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactStatItem(
                icon = Icons.Outlined.Timer,
                value = formatDuration(totalListenDurationMs),
                label = "聆听时长",
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .width(0.5.dp)
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
            )

            CompactStatItem(
                icon = Icons.Filled.PlayArrow,
                value = "$totalPlayCount",
                label = "播放次数",
                modifier = Modifier.weight(1f)
            )
        }

        // ── Divider ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        )

        // ── Favorites row ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onFavoritesClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (favoritesCoverUrl.isNotEmpty()) {
                CoverImage(
                    url = favoritesCoverUrl,
                    contentDescription = "我的收藏",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "我的收藏",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    "$favoritesCount 首歌曲",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onImportClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.CloudDownload,
                    contentDescription = "导入歌单",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onCreateClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.AddCircle,
                    contentDescription = "新建歌单",
                    tint = QQMusicGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ── Divider ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        )

        // ── Downloaded row ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onDownloadedClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "已下载",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    "$downloadedCount 首歌曲",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onLocalMusicClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "本地音乐",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    "$localTrackCount 首歌曲",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CompactStatItem(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = QQMusicGreen,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Playlist Row ────────────────────────────────────────────────────────────────

@Composable
private fun PlaylistRow(
    name: String,
    coverUrl: String,
    trackCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (coverUrl.isNotEmpty()) {
            CoverImage(
                url = coverUrl,
                contentDescription = name,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${trackCount}首歌",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Ranked Track Item ───────────────────────────────────────────────────────────

@Composable
private fun RankedTrackItem(
    rank: Int,
    track: Track,
    playCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank number
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (rank <= 3) QQMusicGreen
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Cover
        CoverImage(
            url = track.coverUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Title + Artist
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

        // Play count + platform
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$playCount 次",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = track.platform.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

// ── Dialogs ─────────────────────────────────────────────────────────────────────

@Composable
private fun ImportPlaylistDialog(
    isImporting: Boolean,
    importError: String?,
    onDismiss: () -> Unit,
    onConfirm: (Platform, String, String) -> Unit
) {
    var selectedPlatform by remember { mutableStateOf<Platform?>(null) }
    var sourceInput by remember { mutableStateOf("") }
    var playlistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入歌单") },
        text = {
            Column {
                Text(
                    "选择导入来源：",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Platform.onlinePlatforms.forEach { platform ->
                    ImportSourceItem(
                        name = platform.displayName,
                        selected = selectedPlatform == platform,
                        onClick = { selectedPlatform = platform }
                    )
                }
                selectedPlatform?.let { platform ->
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = sourceInput,
                        onValueChange = { sourceInput = it },
                        label = { Text("歌单链接或歌单 ID") },
                        placeholder = { Text(importSourcePlaceholder(platform)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        label = { Text("本地歌单名称（可选）") },
                        placeholder = { Text("${platform.displayName}歌单") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "支持直接粘贴歌单分享链接、QQ 短分享链，也支持只填歌单 ID；单曲链接和歌曲 ID 不行。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (!importError.isNullOrBlank()) {
                    Text(
                        text = importError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedPlatform?.let { platform ->
                        onConfirm(platform, sourceInput, playlistName)
                    }
                },
                enabled = selectedPlatform != null && sourceInput.isNotBlank() && !isImporting
            ) {
                Text(if (isImporting) "导入中..." else "导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isImporting) { Text("取消") }
        }
    )
}

@Composable
private fun ImportSourceItem(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.CloudDownload,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else QQMusicGreen,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建歌单") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("歌单名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ── Utility ─────────────────────────────────────────────────────────────────────

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}小时${minutes}分"
        minutes > 0 -> "${minutes}分${seconds}秒"
        else -> "${seconds}秒"
    }
}

private fun importSourcePlaceholder(platform: Platform): String {
    return when (platform) {
        Platform.QQ -> "例如：https://y.qq.com/n/ryqq/playlist/9209322004"
        Platform.NETEASE -> "例如：https://music.163.com/#/playlist?id=19723756"
        Platform.KUWO -> "例如：https://www.kuwo.cn/playlist_detail/2891238463"
        Platform.LOCAL -> ""
    }
}
