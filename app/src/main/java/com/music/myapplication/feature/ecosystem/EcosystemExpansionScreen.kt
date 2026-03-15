package com.music.myapplication.feature.ecosystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.domain.model.Track
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.feature.player.PlayerViewModel
import com.music.myapplication.ui.theme.AppShapes
import com.music.myapplication.ui.theme.glassSurface

@Composable
fun EcosystemExpansionScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onOpenVideoPlayer: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val staticState by playerViewModel.staticUiState.collectAsStateWithLifecycle()
    val currentTrack = staticState.currentTrack
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val topGradient = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.38f else 0.18f)
    val bottomGradient = MaterialTheme.colorScheme.secondary.copy(alpha = if (isDark) 0.22f else 0.1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(topGradient, bottomGradient, Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(42.dp)
                        .glassSurface(shape = RoundedCornerShape(21.dp))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "生态扩展",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "先把播放器往 MV 和车机场景推一截，社区壳子先占坑别乱卷。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            HeroPanel(
                currentTrack = currentTrack,
                onOpenVideoPlayer = onOpenVideoPlayer
            )

            Spacer(modifier = Modifier.height(16.dp))

            DesignCueRow(cues = listOf("大封面", "少按钮", "强层次"))

            Spacer(modifier = Modifier.height(16.dp))

            FeaturePanel(
                icon = Icons.Default.DirectionsCar,
                title = "Android Auto",
                subtitle = "媒体树先收敛成 收藏 / 最近播放 / 歌单，车机场景就该大块信息、少碎操作。",
                status = "本轮已接入",
                detail = "服务侧升级为 MediaLibraryService，继续复用现有播放控制链，不单独再造一套状态中心。"
            )

            Spacer(modifier = Modifier.height(12.dp))

            FeaturePanel(
                icon = Icons.Default.OndemandVideo,
                title = "MV / 视频页",
                subtitle = "独立视频播放器走沉浸路线，横竖屏都能站住，没拿到地址也得给用户留个体面空态。",
                status = if (currentTrack != null) "可从当前歌曲试试" else "等待当前播放",
                detail = if (currentTrack != null) {
                    "已就绪：${currentTrack.title} · ${currentTrack.artist}"
                } else {
                    "先播一首歌，MV 剧场才能顺手带上上下文。"
                },
                actionLabel = if (currentTrack != null) "打开 MV 剧场" else null,
                onAction = currentTrack?.let { track -> { onOpenVideoPlayer(track) } }
            )

            Spacer(modifier = Modifier.height(12.dp))

            FeaturePanel(
                icon = Icons.Default.Forum,
                title = "社区外壳",
                subtitle = "第 2 周前半再接 Feed / 详情只读骨架，这周先把视觉语言和入口秩序统一住。",
                status = "下一步",
                detail = "别急着把点赞、评论发布、分享这些高变更项一股脑全扛上来。"
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeroPanel(
    currentTrack: Track?,
    onOpenVideoPlayer: (Track) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape = RoundedCornerShape(AppShapes.XLarge))
            .padding(20.dp)
    ) {
        Text(
            text = "Week 1 · 后半",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "MV 剧场 + 车机媒体树",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "参考主流播放器的沉浸做法，先把封面氛围、控制密度和层次感打到位，再往视频与社区继续扩。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(18.dp))

        if (currentTrack != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverImage(
                    url = currentTrack.coverUrl,
                    contentDescription = currentTrack.title,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "当前播放",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentTrack.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(onClick = { onOpenVideoPlayer(currentTrack) }) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("MV 剧场")
                }
            }
        } else {
            EmptyHeroHint()
        }
    }
}

@Composable
private fun EmptyHeroHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
            .padding(16.dp)
    ) {
        Text(
            text = "还没拿到当前歌曲",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "先随便播一首，MV 页就能顺手带上歌曲信息，不至于页面光秃秃像没睡醒。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DesignCueRow(cues: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        cues.forEachIndexed { index, cue ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
            ) {
                Text(
                    text = cue,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            if (index != cues.lastIndex) {
                Spacer(modifier = Modifier.width(10.dp))
            }
        }
    }
}

@Composable
private fun FeaturePanel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    status: String,
    detail: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape = RoundedCornerShape(AppShapes.Large))
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (actionLabel != null && onAction != null) {
                    IconButton(onClick = onAction) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = actionLabel
                        )
                    }
                }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}
