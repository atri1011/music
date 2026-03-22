package com.music.myapplication.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.domain.model.Track
import com.music.myapplication.feature.components.EmptyStateView
import com.music.myapplication.feature.components.MediaListItem
import com.music.myapplication.feature.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedScreen(
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: DownloadedViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("下载管理") },
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
        if (state.downloading.isEmpty() && state.failed.isEmpty() && state.downloaded.isEmpty()) {
            EmptyStateView(
                icon = Icons.Outlined.DownloadDone,
                title = "还没有下载任务",
                subtitle = "开始下载后，这里会按“正在下载 / 下载失败 / 已下载”帮你管起来。",
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
                if (state.downloading.isNotEmpty()) {
                    item {
                        DownloadSectionHeader(
                            title = "正在下载",
                            subtitle = "${state.downloading.size} 首任务仍在进行中"
                        )
                    }
                    itemsIndexed(
                        state.downloading,
                        key = { _, item -> "downloading:${item.track.platform.id}:${item.track.id}" }
                    ) { _, item ->
                        DownloadStateRow(
                            item = item,
                            statusText = item.progressPercent
                                .takeIf { it > 0 }
                                ?.let { "下载中 · $it%" }
                                ?: "下载中…",
                            statusColor = MaterialTheme.colorScheme.primary,
                            rowClickable = false,
                            onPrimaryAction = { viewModel.cancelDownload(item.track) },
                            primaryActionLabel = "取消下载",
                            primaryIcon = Icons.Filled.Close
                        )
                    }
                }

                if (state.failed.isNotEmpty()) {
                    item {
                        DownloadSectionHeader(
                            title = "下载失败",
                            subtitle = "${state.failed.size} 首任务可重试或删除"
                        )
                    }
                    itemsIndexed(
                        state.failed,
                        key = { _, item -> "failed:${item.track.platform.id}:${item.track.id}" }
                    ) { _, item ->
                        DownloadStateRow(
                            item = item,
                            statusText = item.failureReason.ifBlank { "下载失败，请稍后重试" },
                            statusColor = MaterialTheme.colorScheme.error,
                            rowClickable = false,
                            onPrimaryAction = { playerViewModel.downloadTrack(item.track) },
                            primaryActionLabel = "重试",
                            primaryIcon = Icons.Filled.Refresh,
                            onSecondaryAction = { viewModel.removeDownloaded(item.track) },
                            secondaryActionLabel = "删除"
                        )
                    }
                }

                if (state.downloaded.isNotEmpty()) {
                    item {
                        DownloadSectionHeader(
                            title = "已下载",
                            subtitle = "${state.downloaded.size} 首歌曲已离线可播"
                        )
                    }
                    itemsIndexed(
                        state.downloaded,
                        key = { _, item -> "downloaded:${item.track.platform.id}:${item.track.id}" }
                    ) { index, item ->
                        DownloadStateRow(
                            item = item,
                            index = index,
                            onPlay = {
                                playerViewModel.playTrack(item.track, state.downloadedTracks, index)
                            },
                            statusText = "离线文件已同步到本地音乐",
                            statusColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onPrimaryAction = {
                                playerViewModel.playTrack(item.track, state.downloadedTracks, index)
                            },
                            primaryActionLabel = "播放",
                            primaryIcon = Icons.Filled.PlayArrow,
                            onSecondaryAction = { viewModel.removeDownloaded(item.track) },
                            secondaryActionLabel = "删除"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadSectionHeader(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DownloadStateRow(
    item: DownloadedUiTrack,
    statusText: String,
    statusColor: Color,
    modifier: Modifier = Modifier,
    index: Int? = null,
    onPlay: (() -> Unit)? = null,
    rowClickable: Boolean = onPlay != null,
    onPrimaryAction: (() -> Unit)? = null,
    primaryActionLabel: String? = null,
    primaryIcon: ImageVector? = null,
    onSecondaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        MediaListItem(
            track = item.track,
            index = index,
            onClick = onPlay ?: {},
            enabled = rowClickable,
            onMoreClick = null
        )
        Column(
            modifier = Modifier.padding(start = 80.dp, end = 20.dp, bottom = 8.dp)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor
            )
            Row(
                modifier = Modifier.padding(top = 8.dp)
            ) {
                if (onPrimaryAction != null && primaryActionLabel != null && primaryIcon != null) {
                    FilledTonalButton(onClick = onPrimaryAction) {
                        Icon(
                            imageVector = primaryIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(primaryActionLabel)
                    }
                }
                if (onSecondaryAction != null && secondaryActionLabel != null) {
                    TextButton(
                        onClick = onSecondaryAction,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(secondaryActionLabel)
                    }
                }
            }
        }
    }
}
