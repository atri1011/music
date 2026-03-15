package com.music.myapplication.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.domain.model.Track
import com.music.myapplication.ui.theme.AppShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val staticState by playerViewModel.staticUiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val queue = staticState.queue
    val currentIndex = staticState.currentIndex
    val itemHeights = remember { mutableMapOf<Int, Int>() }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val queueStatusText = when {
        queue.isEmpty() -> "暂无待播歌曲"
        currentIndex in queue.indices -> "正在播放 ${currentIndex + 1} / ${queue.size} · 长按拖拽排序"
        else -> "共 ${queue.size} 首 · 长按拖拽排序"
    }

    LaunchedEffect(queue.size) {
        if (draggingIndex > queue.lastIndex) {
            draggingIndex = -1
            dragOffsetY = 0f
        }
    }

    fun moveDraggingItem(originIndex: Int, dragAmountY: Float) {
        if (queue.isEmpty()) return
        dragOffsetY += dragAmountY
        var activeIndex = draggingIndex.takeIf { it >= 0 } ?: originIndex
        val itemHeight = (itemHeights[activeIndex] ?: itemHeights[originIndex] ?: 0).toFloat()
        if (itemHeight <= 0f) return
        val threshold = itemHeight / 2f

        while (dragOffsetY > threshold && activeIndex < queue.lastIndex) {
            playerViewModel.moveQueueItem(activeIndex, activeIndex + 1)
            activeIndex += 1
            dragOffsetY -= itemHeight
        }

        while (dragOffsetY < -threshold && activeIndex > 0) {
            playerViewModel.moveQueueItem(activeIndex, activeIndex - 1)
            activeIndex -= 1
            dragOffsetY += itemHeight
        }

        draggingIndex = activeIndex
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "播放队列 (${queue.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = queueStatusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (queue.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            playerViewModel.clearQueue()
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("清空")
                    }
                }
            }

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "播放队列为空",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(
                        items = queue,
                        key = { index, track -> "${track.platform.id}:${track.id}:${track.title}:${track.artist}:$index" }
                    ) { index, track ->
                        QueueItem(
                            track = track,
                            isCurrent = index == currentIndex,
                            isDragging = index == draggingIndex,
                            dragOffsetY = if (index == draggingIndex) dragOffsetY else 0f,
                            onPlay = { playerViewModel.playQueueItem(index) },
                            onRemove = { playerViewModel.removeFromQueue(index) },
                            onMeasured = { heightPx -> itemHeights[index] = heightPx },
                            onDragStart = {
                                draggingIndex = index
                                dragOffsetY = 0f
                            },
                            onDrag = { dragAmountY -> moveDraggingItem(index, dragAmountY) },
                            onDragEnd = {
                                draggingIndex = -1
                                dragOffsetY = 0f
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueItem(
    track: Track,
    isCurrent: Boolean,
    isDragging: Boolean,
    dragOffsetY: Float,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onMeasured: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        isDragging -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffsetY }
            .clip(RoundedCornerShape(AppShapes.Medium))
            .background(bgColor)
            .onSizeChanged { onMeasured(it.height) }
            .clickable(enabled = !isDragging, onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = "长按拖拽排序",
            modifier = Modifier
                .size(20.dp)
                .pointerInput(track.id, track.platform.id, isDragging) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.y)
                        }
                    )
                },
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = if (isDragging) 0.9f else 0.4f
            )
        )
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
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

        if (isCurrent) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppShapes.Tiny))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "播放中",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        IconButton(
            onClick = onRemove,
            enabled = !isDragging,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "移除",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
