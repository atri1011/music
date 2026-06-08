package com.music.myapplication.feature.library

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
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
import com.music.myapplication.domain.model.Playlist
import com.music.myapplication.domain.model.PlaylistFolder
import com.music.myapplication.domain.model.SmartPlaylist
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.ui.theme.AppShapes
import com.music.myapplication.ui.theme.QQMusicGreen
import com.music.myapplication.ui.theme.appPremiumBackground
import com.music.myapplication.ui.theme.glassSurface
import kotlin.math.abs

// Accent colors for quick-access tiles
private val FavoriteAccent = Color(0xFFFF6B8A)
private val LocalAccent = Color(0xFF5B8DEF)

@Composable
fun LibraryScreen(
    onNavigateToPlaylist: (id: String, name: String) -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToPlayRanking: () -> Unit = {},
    onNavigateToPlayHistory: () -> Unit = {},
    onNavigateToMusicYearReport: () -> Unit = {},
    onNavigateToDownloaded: () -> Unit = {},
    onNavigateToLocalMusic: () -> Unit = {},
    onNavigateToSmartPlaylist: (id: String, name: String) -> Unit = { _, _ -> },
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingPlaylistCoverId by remember { mutableStateOf<String?>(null) }
    val playlistCoverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val playlistId = pendingPlaylistCoverId
        pendingPlaylistCoverId = null
        if (playlistId != null && uri != null) {
            viewModel.updatePlaylistCover(playlistId, uri.toString())
        }
    }

    LaunchedEffect(state.importedPlaylist) {
        state.importedPlaylist?.let { destination ->
            onNavigateToPlaylist(destination.playlistId, destination.playlistName)
            viewModel.consumeImportedPlaylist()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .appPremiumBackground()
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
            item {
                NeteaseAccountHeaderCard(
                    state = state,
                    onClick = { viewModel.showLoginSheet(true) },
                    onSyncClick = viewModel::syncNeteaseData
                )
            }

            // Stats capsules (listening time + play count)
            item {
                StatsCapsules(
                    totalPlayCount = state.totalPlayCount,
                    totalListenDurationMs = state.totalListenDurationMs,
                    onPlayCountClick = onNavigateToPlayRanking,
                    onListenDurationClick = onNavigateToPlayHistory
                )
            }

            item {
                YearReportBanner(
                    onClick = onNavigateToMusicYearReport,
                    totalPlayCount = state.totalPlayCount
                )
            }

            // Quick access grid (Favorites / Downloaded / Local)
            item {
                QuickAccessGrid(
                    favoritesCount = state.favorites.size,
                    onFavoritesClick = onNavigateToFavorites,
                    downloadedCount = state.downloadedCount,
                    onDownloadedClick = onNavigateToDownloaded,
                    localTrackCount = state.localTrackCount,
                    onLocalMusicClick = onNavigateToLocalMusic
                )
            }

            item(key = "smart_playlist_header") {
                SmartPlaylistSectionHeader()
            }
            items(
                count = state.smartPlaylists.size,
                key = { index -> "smart:${state.smartPlaylists[index].rule.id}" }
            ) { index ->
                val smartPlaylist = state.smartPlaylists[index]
                SmartPlaylistRow(
                    playlist = smartPlaylist,
                    onClick = {
                        onNavigateToSmartPlaylist(smartPlaylist.rule.id, smartPlaylist.rule.title)
                    }
                )
            }

            // Playlist section header with import/create actions
            item {
                PlaylistSectionHeader(
                    onImportClick = { viewModel.showImportDialog(true) },
                    onCreateFolderClick = { viewModel.showCreateFolderDialog(true) },
                    onCreateClick = { viewModel.showCreateDialog(true) }
                )
            }

            // User playlists
            val playlists = state.playlists
            val folders = state.playlistFolders
            val folderIds = folders.map { it.id }.toSet()
            folders.forEach { folder ->
                val folderPlaylists = playlists.filter { it.folderId == folder.id }
                item(key = "folder:${folder.id}") {
                    PlaylistFolderHeader(
                        folder = folder,
                        playlistCount = folderPlaylists.size,
                        onDelete = { viewModel.deletePlaylistFolder(folder.id) }
                    )
                }
                items(
                    count = folderPlaylists.size,
                    key = { index -> "folder:${folder.id}:${folderPlaylists[index].id}" }
                ) { index ->
                    val playlist = folderPlaylists[index]
                    PlaylistRow(
                        playlist = playlist,
                        folderName = folder.name,
                        onClick = { onNavigateToPlaylist(playlist.id, playlist.name) },
                        onEditCover = {
                            pendingPlaylistCoverId = playlist.id
                            playlistCoverPicker.launch("image/*")
                        },
                        onMoveToFolder = { viewModel.showMovePlaylistFolderDialog(playlist) },
                        onDelete = { viewModel.deletePlaylist(playlist.id) }
                    )
                }
            }
            val ungroupedPlaylists = playlists.filter { it.folderId == null || it.folderId !in folderIds }
            if (ungroupedPlaylists.isNotEmpty()) {
                if (folders.isNotEmpty()) {
                    item(key = "folder:ungrouped") {
                        UngroupedPlaylistHeader(count = ungroupedPlaylists.size)
                    }
                }
                items(
                    count = ungroupedPlaylists.size,
                    key = { index -> "folder:ungrouped:${ungroupedPlaylists[index].id}" }
                ) { index ->
                    val playlist = ungroupedPlaylists[index]
                    PlaylistRow(
                        playlist = playlist,
                        folderName = null,
                        onClick = { onNavigateToPlaylist(playlist.id, playlist.name) },
                        onEditCover = {
                            pendingPlaylistCoverId = playlist.id
                            playlistCoverPicker.launch("image/*")
                        },
                        onMoveToFolder = { viewModel.showMovePlaylistFolderDialog(playlist) },
                        onDelete = { viewModel.deletePlaylist(playlist.id) }
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

        if (state.showCreateFolderDialog) {
            CreatePlaylistDialog(
                title = "新建文件夹",
                label = "文件夹名称",
                placeholder = "例如：通勤、华语、运动",
                confirmText = "创建",
                onDismiss = { viewModel.showCreateFolderDialog(false) },
                onConfirm = viewModel::createPlaylistFolder
            )
        }

        state.folderMoveTarget?.let { playlist ->
            MovePlaylistFolderDialog(
                playlist = playlist,
                folders = state.playlistFolders,
                onDismiss = { viewModel.showMovePlaylistFolderDialog(null) },
                onMove = { folderId -> viewModel.movePlaylistToFolder(playlist.id, folderId) }
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

        if (state.showLoginSheet) {
            NeteaseLoginSheet(
                state = state,
                onDismiss = { viewModel.showLoginSheet(false) },
                onPasswordLogin = viewModel::loginWithPassword,
                onSendCaptcha = viewModel::sendCaptcha,
                onCaptchaLogin = viewModel::loginWithCaptcha,
                onStartQrLogin = viewModel::prepareQrLogin,
                onSyncClick = viewModel::syncNeteaseData,
                onLogout = viewModel::logoutNeteaseAccount
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
    val primary = MaterialTheme.colorScheme.primary
    val isDark = bgColor.luminance() < 0.5f

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

        // Gradient: semi-transparent at top -> fully opaque theme bg at bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            primary.copy(alpha = if (isDark) 0.14f else 0.08f),
                            bgColor.copy(alpha = 0.62f),
                            bgColor.copy(alpha = 0.94f)
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
            val distFromCenter = abs(offsetFromCenter).toInt()
            val coverScale = when (distFromCenter) {
                0 -> 1.0f
                1 -> 0.90f
                else -> 0.80f
            }
            val blurAmount = when (distFromCenter) {
                0 -> 0.dp
                1 -> 4.dp
                else -> 8.dp
            }

            CoverImage(
                url = track.coverUrl,
                contentDescription = track.title,
                modifier = Modifier
                    .zIndex(zIdx)
                    .offset(x = spacing * offsetFromCenter)
                    .graphicsLayer {
                        rotationZ = angles[index]
                        scaleX = coverScale
                        scaleY = coverScale
                        shadowElevation = if (distFromCenter == 0) 8f else 2f
                    }
                    .then(
                        if (blurAmount > 0.dp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            Modifier.blur(blurAmount)
                        else Modifier
                    )
                    .size(coverSize)
                    .clip(RoundedCornerShape(AppShapes.Small))
            )
        }
    }
}

// ── Stats Capsules ──────────────────────────────────────────────────────────────

@Composable
private fun StatsCapsules(
    totalPlayCount: Int,
    totalListenDurationMs: Long,
    onPlayCountClick: () -> Unit,
    onListenDurationClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCapsule(
            icon = Icons.Outlined.Timer,
            value = formatDuration(totalListenDurationMs),
            label = "聆听时长",
            onClick = onListenDurationClick,
            modifier = Modifier.weight(1f)
        )
        StatCapsule(
            icon = Icons.Filled.PlayArrow,
            value = "$totalPlayCount",
            label = "播放次数",
            onClick = onPlayCountClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCapsule(
    icon: ImageVector,
    value: String,
    label: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .glassSurface(
                shape = RoundedCornerShape(AppShapes.Medium),
                pressScale = onClick != null
            )
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun YearReportBanner(
    totalPlayCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .glassSurface(shape = RoundedCornerShape(AppShapes.Medium), pressScale = true)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "音乐年度报告",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (totalPlayCount > 0) "生成你的本地听歌旅程" else "播放几首歌后这里会有内容",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "查看",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ── Quick Access Grid ───────────────────────────────────────────────────────────

@Composable
private fun QuickAccessGrid(
    favoritesCount: Int,
    onFavoritesClick: () -> Unit,
    downloadedCount: Int,
    onDownloadedClick: () -> Unit,
    localTrackCount: Int,
    onLocalMusicClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickAccessTile(
            icon = Icons.Outlined.FavoriteBorder,
            title = "收藏",
            count = favoritesCount,
            onClick = onFavoritesClick,
            accentColor = FavoriteAccent,
            modifier = Modifier.weight(1f)
        )
        QuickAccessTile(
            icon = Icons.Outlined.MusicNote,
            title = "下载",
            count = downloadedCount,
            onClick = onDownloadedClick,
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        QuickAccessTile(
            icon = Icons.AutoMirrored.Outlined.QueueMusic,
            title = "本地",
            count = localTrackCount,
            onClick = onLocalMusicClick,
            accentColor = LocalAccent,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickAccessTile(
    icon: ImageVector,
    title: String,
    count: Int,
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .glassSurface(shape = RoundedCornerShape(AppShapes.Medium), pressScale = true)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "$count 首",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Section Headers ─────────────────────────────────────────────────────────────

@Composable
private fun SmartPlaylistSectionHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
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
            text = "智能歌单",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "本地规则自动更新",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SmartPlaylistRow(
    playlist: SmartPlaylist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .glassSurface(shape = RoundedCornerShape(AppShapes.Medium), pressScale = true)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmartPlaylistPreviewCovers(playlist.previewTracks)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.rule.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${playlist.rule.description} · ${playlist.trackCount} 首",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SmartPlaylistPreviewCovers(tracks: List<Track>) {
    Box(
        modifier = Modifier.size(52.dp),
        contentAlignment = Alignment.Center
    ) {
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            return
        }

        tracks.take(3).forEachIndexed { index, track ->
            CoverImage(
                url = track.coverUrl,
                contentDescription = track.title,
                modifier = Modifier
                    .zIndex((3 - index).toFloat())
                    .offset(x = (index * 8).dp)
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(10.dp)
                    )
            )
        }
    }
}

@Composable
private fun PlaylistSectionHeader(
    onImportClick: () -> Unit,
    onCreateFolderClick: () -> Unit,
    onCreateClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "我的歌单",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f)
        )
        SectionHeaderActionButton(
            onClick = onImportClick,
            icon = Icons.Outlined.CloudDownload,
            contentDescription = "导入歌单",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SectionHeaderActionButton(
            onClick = onCreateFolderClick,
            icon = Icons.Default.CreateNewFolder,
            contentDescription = "新建文件夹",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SectionHeaderActionButton(
            onClick = onCreateClick,
            icon = Icons.Filled.AddCircle,
            contentDescription = "新建歌单",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PlaylistFolderHeader(
    folder: PlaylistFolder,
    playlistCount: Int,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$playlistCount 个歌单",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除文件夹",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.46f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun UngroupedPlaylistHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "未分组",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$count 个歌单",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionHeaderActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    tint: Color
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .glassSurface(shape = RoundedCornerShape(999.dp), pressScale = true)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ── Playlist Row ────────────────────────────────────────────────────────────────

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    folderName: String?,
    onClick: () -> Unit,
    onEditCover: () -> Unit,
    onMoveToFolder: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (playlist.coverUrl.isNotEmpty()) {
            CoverImage(
                url = playlist.coverUrl,
                contentDescription = playlist.name,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
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
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = buildPlaylistSubtitle(playlist.trackCount, folderName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onEditCover, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Outlined.Image,
                contentDescription = "更换封面",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onMoveToFolder, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.DriveFileMove,
                contentDescription = "移动到文件夹",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── Dialogs ─────────────────────────────────────────────────────────────────────

@Composable
private fun MovePlaylistFolderDialog(
    playlist: Playlist,
    folders: List<PlaylistFolder>,
    onDismiss: () -> Unit,
    onMove: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动到文件夹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                MoveFolderOption(
                    title = "未分组",
                    selected = playlist.folderId == null,
                    onClick = { onMove(null) }
                )
                folders.forEach { folder ->
                    MoveFolderOption(
                        title = folder.name,
                        selected = playlist.folderId == folder.id,
                        onClick = { onMove(folder.id) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun MoveFolderOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppShapes.ExtraSmall))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (selected) "$title · 当前" else title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

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
                        text = "支持直接粘贴电脑链接、手机分享文案、短分享链，也支持只填歌单 ID；单曲链接和歌曲 ID 不行。",
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
            .clip(RoundedCornerShape(AppShapes.ExtraSmall))
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
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    title: String = "新建歌单",
    label: String = "歌单名称",
    placeholder: String? = null,
    confirmText: String = "创建",
    enabled: Boolean = true
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {
            if (enabled) onDismiss()
        },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(label) },
                placeholder = placeholder?.let { { Text(it) } },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank() && enabled
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = enabled) { Text("取消") }
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

private fun buildPlaylistSubtitle(trackCount: Int, folderName: String?): String {
    val countText = "$trackCount 首歌曲"
    return folderName?.takeIf { it.isNotBlank() }?.let { "$countText · $it" } ?: countText
}

private fun importSourcePlaceholder(platform: Platform): String {
    return when (platform) {
        Platform.QQ -> "例如：https://y.qq.com/n/ryqq/playlist/9209322004"
        Platform.NETEASE -> "例如：https://music.163.com/#/playlist?id=19723756"
        Platform.KUWO -> "例如：https://www.kuwo.cn/playlist_detail/2891238463"
        Platform.LOCAL -> ""
    }
}
