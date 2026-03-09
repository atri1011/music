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
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Share
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

data class PlayerMenuItem(
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
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val menuItems = listOf(
        PlayerMenuItem(
            icon = Icons.Default.Bedtime,
            label = "定时关闭",
            onClick = {
                onDismiss()
                onSleepTimer()
            }
        ),
        PlayerMenuItem(
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            label = "播放队列",
            onClick = {
                onDismiss()
                onQueueManager()
            }
        ),
        PlayerMenuItem(
            icon = Icons.Default.GraphicEq,
            label = "音质选择",
            enabled = false,
            disabledHint = "待后续能力接入"
        ) {},
        PlayerMenuItem(
            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
            label = "添加到歌单",
            enabled = false,
            disabledHint = "待后续能力接入"
        ) {},
        PlayerMenuItem(
            icon = Icons.Default.PersonSearch,
            label = "查看歌手",
            enabled = false,
            disabledHint = "待后续能力接入"
        ) {},
        PlayerMenuItem(
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

@Composable
private fun MenuRow(item: PlayerMenuItem) {
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
