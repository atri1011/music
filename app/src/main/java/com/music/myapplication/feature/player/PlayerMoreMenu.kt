package com.music.myapplication.feature.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
            enabled = false,
            disabledHint = "待后续能力接入"
        ) {},
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
    onDownload: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val menuItems = listOf(
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
