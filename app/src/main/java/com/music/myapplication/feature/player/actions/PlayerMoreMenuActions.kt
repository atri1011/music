package com.music.myapplication.feature.player.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Download
import com.music.myapplication.feature.player.MenuActionItem

fun buildPlayerMoreMenuItems(
    onDismiss: () -> Unit,
    onSleepTimer: () -> Unit,
    onVideoPlayer: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    onSpeedPicker: () -> Unit,
    onEqualizer: () -> Unit,
    onSimilarTracks: () -> Unit,
    currentSpeed: Float
): List<MenuActionItem> = listOf(
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
        icon = Icons.Default.GraphicEq,
        label = "均衡器",
        onClick = {
            onDismiss()
            onEqualizer()
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
        icon = Icons.AutoMirrored.Filled.QueueMusic,
        label = "相似歌曲",
        onClick = {
            onDismiss()
            onSimilarTracks()
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

fun buildTrackMoreMenuItems(
    onDismiss: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onArtist: (() -> Unit)?,
    onAlbum: (() -> Unit)?,
    onShare: () -> Unit
): List<MenuActionItem> = buildList {
    if (onToggleFavorite != null) {
        add(
            MenuActionItem(
                icon = Icons.Default.Favorite,
                label = "收藏 / 取消收藏",
                onClick = {
                    onDismiss()
                    onToggleFavorite()
                }
            )
        )
    }
    add(
        MenuActionItem(
            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
            label = "添加到歌单",
            onClick = {
                onDismiss()
                onAddToPlaylist()
            }
        )
    )
    add(
        MenuActionItem(
            icon = Icons.Outlined.Download,
            label = "下载",
            onClick = {
                onDismiss()
                onDownload()
            }
        )
    )
    add(
        MenuActionItem(
            icon = Icons.Default.PersonSearch,
            label = "查看歌手",
            enabled = onArtist != null,
            disabledHint = "当前来源暂不支持",
            onClick = {
                onDismiss()
                onArtist?.invoke()
            }
        )
    )
    add(
        MenuActionItem(
            icon = Icons.Default.LibraryMusic,
            label = "查看专辑",
            enabled = onAlbum != null,
            disabledHint = "当前歌曲缺少专辑信息",
            onClick = {
                onDismiss()
                onAlbum?.invoke()
            }
        )
    )
    add(
        MenuActionItem(
            icon = Icons.Default.Share,
            label = "分享",
            onClick = {
                onDismiss()
                onShare()
            }
        )
    )
}
