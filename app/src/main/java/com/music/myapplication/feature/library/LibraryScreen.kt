package com.music.myapplication.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.MusicNote
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Playlist
import com.music.myapplication.domain.model.PlaylistFolder
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.ui.theme.AppShapes
import com.music.myapplication.ui.theme.QQMusicGreen

private val StitchBackground: Color
    @Composable get() = MaterialTheme.colorScheme.background
private val StitchCard: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainer
private val StitchText: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface
private val StitchMutedText: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val StitchBorder: Color
    @Composable get() = MaterialTheme.colorScheme.outline
private val StitchAccentGreen: Color
    @Composable get() = MaterialTheme.colorScheme.primary
private val StitchBannerStart: Color
    @Composable get() = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)

@Composable
fun LibraryScreen(
    onNavigateToPlaylist: (id: String, name: String) -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToPlayRanking: () -> Unit = {},
    onNavigateToPlayHistory: () -> Unit = {},
    onNavigateToMusicYearReport: () -> Unit = {},
    onNavigateToDownloaded: () -> Unit = {},
    onNavigateToLocalMusic: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
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
            .background(StitchBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                LibraryProfileSection(
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

            // Playlist section header with import/create actions
            item {
                PlaylistSectionHeader(
                    onCreateClick = { viewModel.showCreateDialog(true) },
                    onManageClick = { viewModel.showCreateFolderDialog(true) }
                )
            }

            // User playlists
            val playlists = state.playlists
            val folders = state.playlistFolders
            val folderIds = folders.map { it.id }.toSet()
            folders.forEach { folder ->
                val folderPlaylists = playlists.filter { it.folderId == folder.id }
                item(key = "folder:${folder.id}") {
                    PlaylistFolderRow(
                        folder = folder,
                        playlistCount = folderPlaylists.size,
                        onClick = { viewModel.showCreateFolderDialog(true) }
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
                        onClick = { onNavigateToPlaylist(playlist.id, playlist.name) }
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
                        onClick = { onNavigateToPlaylist(playlist.id, playlist.name) }
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

// ── Stats Capsules ──────────────────────────────────────────────────────────────

@Composable
private fun LibraryProfileSection(
    state: LibraryUiState,
    onClick: () -> Unit,
    onSyncClick: () -> Unit
) {
    val account = state.account
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(2.dp, StitchCard, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!account?.avatarUrl.isNullOrBlank()) {
                    CoverImage(
                        url = account?.avatarUrl.orEmpty(),
                        contentDescription = account?.nickname ?: "头像",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = account?.nickname ?: "听音乐的人",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = StitchText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        state.isSyncingNeteaseData -> "同步中"
                        account != null -> "已连接同步"
                        state.isNeteaseConfigured -> "点击连接同步"
                        else -> "本地资料库"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = StitchMutedText
                )
            }
        }

        IconButton(
            onClick = onSyncClick,
            enabled = account != null && !state.isSyncingNeteaseData,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "同步",
                tint = StitchAccentGreen,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

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
            value = formatDuration(totalListenDurationMs),
            label = "聆听时长",
            onClick = onListenDurationClick,
            modifier = Modifier.weight(1f)
        )
        StatCapsule(
            value = "${totalPlayCount}次",
            label = "播放次数",
            onClick = onPlayCountClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCapsule(
    value: String,
    label: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .stitchCard()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(16.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = StitchText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun YearReportBanner(
    totalPlayCount: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(StitchBannerStart, StitchCard)
                )
            )
            .border(0.5.dp, StitchBorder.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(end = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(72.dp)
                    .background(StitchAccentGreen)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "2024 音乐年度报告",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = StitchText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (totalPlayCount > 0) "生成你的本地听歌旅程" else "播放几首歌后这里会有内容",
                    style = MaterialTheme.typography.labelSmall,
                    color = StitchMutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "查看",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
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
            icon = Icons.Default.Favorite,
            title = "收藏",
            count = favoritesCount,
            onClick = onFavoritesClick,
            accentColor = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        QuickAccessTile(
            icon = Icons.Default.Download,
            title = "下载",
            count = downloadedCount,
            onClick = onDownloadedClick,
            accentColor = StitchAccentGreen,
            modifier = Modifier.weight(1f)
        )
        QuickAccessTile(
            icon = Icons.AutoMirrored.Filled.ListAlt,
            title = "本地",
            count = localTrackCount,
            onClick = onLocalMusicClick,
            accentColor = MaterialTheme.colorScheme.tertiary,
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
            .stitchCard()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = StitchText
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Section Headers ─────────────────────────────────────────────────────────────

@Composable
private fun PlaylistSectionHeader(
    onCreateClick: () -> Unit,
    onManageClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StitchSectionTitle(
            title = "我的歌单",
            modifier = Modifier.weight(1f)
        )
        PlaylistHeaderTextAction(
            onClick = onCreateClick,
            icon = Icons.Default.Add,
            text = "新建"
        )
        Spacer(modifier = Modifier.width(12.dp))
        PlaylistHeaderTextAction(
            onClick = onManageClick,
            icon = Icons.Default.Settings,
            text = "管理"
        )
    }
}

@Composable
private fun PlaylistHeaderTextAction(
    onClick: () -> Unit,
    icon: ImageVector,
    text: String
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlaylistFolderRow(
    folder: PlaylistFolder,
    playlistCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(0.5.dp, StitchBorder.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = StitchText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$playlistCount 个歌单",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
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

// ── Playlist Row ────────────────────────────────────────────────────────────────

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    folderName: String?,
    onClick: () -> Unit
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
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = StitchText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = buildPlaylistSubtitle(playlist.trackCount, folderName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
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

@Composable
private fun Modifier.stitchCard(
    shape: RoundedCornerShape = RoundedCornerShape(16.dp)
): Modifier {
    val darkTheme = isSystemInDarkTheme()
    return this
        .shadow(
            elevation = 4.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = if (darkTheme) 0.08f else 0.04f),
            spotColor = Color.Black.copy(alpha = if (darkTheme) 0.10f else 0.05f)
        )
        .clip(shape)
        .background(StitchCard)
        .border(0.5.dp, StitchBorder.copy(alpha = 0.45f), shape)
}

@Composable
private fun StitchSectionTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(StitchAccentGreen)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = StitchText
        )
    }
}

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
