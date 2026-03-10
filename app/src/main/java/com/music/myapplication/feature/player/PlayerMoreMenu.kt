package com.music.myapplication.feature.player

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.domain.model.Playlist
import com.music.myapplication.domain.model.Track
import com.music.myapplication.feature.library.CreatePlaylistDialog
import kotlinx.coroutines.launch

data class MenuActionItem(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val disabledHint: String? = null,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMoreMenu(
    onDismiss: () -> Unit,
    onSleepTimer: () -> Unit,
    onQueueManager: () -> Unit,
    onVideoPlayer: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    onSpeedPicker: () -> Unit,
    onEqualizer: () -> Unit,
    currentSpeed: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val menuItems = listOf(
        MenuActionItem(
            icon = Icons.Default.Bedtime,
            label = "定时关闭",
            onClick = {
                onDismiss()
                onSleepTimer()
            }
        ),
        MenuActionItem(
            icon = Icons.Default.Speed,
            label = if (currentSpeed != 1.0f) "倍速 (${currentSpeed}x)" else "倍速",
            onClick = {
                onDismiss()
                onSpeedPicker()
            }
        ),
        MenuActionItem(
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            label = "播放队列",
            onClick = {
                onDismiss()
                onQueueManager()
            }
        ),
        MenuActionItem(
            icon = Icons.Default.OndemandVideo,
            label = "MV / 视频页",
            onClick = {
                onDismiss()
                onVideoPlayer()
            }
        ),
        MenuActionItem(
            icon = Icons.Default.GraphicEq,
            label = "均衡器",
            onClick = {
                onDismiss()
                onEqualizer()
            }
        ),
        MenuActionItem(
            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
            label = "添加到歌单",
            onClick = {
                onDismiss()
                onAddToPlaylist()
            }
        ),
        MenuActionItem(
            icon = Icons.Default.PersonSearch,
            label = "查看歌手",
            enabled = false,
            disabledHint = "待后续能力接入"
        ) {},
        MenuActionItem(
            icon = Icons.Default.Share,
            label = "分享",
            onClick = {
                onDismiss()
                onShare()
            }
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Text(
            text = "更多操作",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            menuItems.forEach { item ->
                MenuRow(item)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMoreMenu(
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val menuItems = listOf(
        MenuActionItem(
            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
            label = "添加到歌单",
            onClick = {
                onDismiss()
                onAddToPlaylist()
            }
        ),
        MenuActionItem(
            icon = Icons.Outlined.Download,
            label = "下载",
            onClick = {
                onDismiss()
                onDownload()
            }
        ),
        MenuActionItem(
            icon = Icons.Default.Share,
            label = "分享",
            onClick = {
                onDismiss()
                onShare()
            }
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Text(
            text = "歌曲操作",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            menuItems.forEach { item ->
                MenuRow(item)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTrackToPlaylistSheet(
    track: Track,
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val playlists by playerViewModel.playlists.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCreateDialog by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submit(action: suspend () -> PlaylistMutationResult) {
        if (isSubmitting) return
        scope.launch {
            isSubmitting = true
            errorMessage = null
            runCatching { action() }
                .onSuccess { result ->
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
                .onFailure { throwable ->
                    errorMessage = throwable.message ?: "添加到歌单失败，请稍后再试。"
                }
            isSubmitting = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Text(
            text = "添加到歌单",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        TextButton(
            onClick = { showCreateDialog = true },
            enabled = !isSubmitting,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("新建歌单并添加")
        }

        when {
            isSubmitting -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "正在保存",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            playlists.isEmpty() -> {
                Text(
                    text = "你现在还没歌单，先建一个再说。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .padding(bottom = 24.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistDestinationRow(
                            playlist = playlist,
                            enabled = !isSubmitting,
                            onClick = {
                                submit {
                                    playerViewModel.addTrackToPlaylist(playlist, track)
                                }
                            }
                        )
                    }
                }
            }
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                showCreateDialog = false
                submit {
                    playerViewModel.createPlaylistAndAddTrack(name, track)
                }
            },
            confirmText = "创建并添加",
            enabled = !isSubmitting
        )
    }
}

@Composable
private fun MenuRow(item: MenuActionItem) {
    val alpha = if (item.enabled) 1f else 0.4f

    TextButton(
        onClick = item.onClick,
        enabled = item.enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                if (!item.enabled && item.disabledHint != null) {
                    Text(
                        text = item.disabledHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistDestinationRow(
    playlist: Playlist,
    enabled: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${playlist.trackCount} 首",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
