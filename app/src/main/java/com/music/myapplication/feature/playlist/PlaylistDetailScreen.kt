package com.music.myapplication.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.core.common.ShareUtils
import com.music.myapplication.domain.model.Track
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.feature.components.EmptyStateView
import com.music.myapplication.feature.components.ErrorView
import com.music.myapplication.feature.components.MediaListItem
import com.music.myapplication.feature.components.ShimmerMediaListItem
import com.music.myapplication.feature.player.AddTrackToPlaylistSheet
import com.music.myapplication.feature.player.PlayerViewModel
import com.music.myapplication.feature.player.TrackMoreMenu
import com.music.myapplication.ui.theme.AppShapes

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    platform: String,
    title: String,
    source: String,
    onBack: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var trackPendingPlaylistAddition by remember { mutableStateOf<Track?>(null) }
    val displayTracks = if (state.isEditMode) state.editingTracks else state.tracks
    val selectedFavoriteCount = state.selectedFavoriteKeys.size
    val areAllFavoritesSelected = displayTracks.isNotEmpty() && selectedFavoriteCount == displayTracks.size
    val itemHeights = remember { mutableMapOf<Int, Int>() }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playlistId, platform, source) {
        viewModel.loadPlaylist(playlistId, platform, title, source)
    }

    LaunchedEffect(state.isEditMode, state.isFavoritesSelectionMode, displayTracks.size) {
        if (!state.isEditMode || draggingIndex > displayTracks.lastIndex) {
            draggingIndex = -1
            dragOffsetY = 0f
        }
        if (state.isEditMode || state.isFavoritesSelectionMode) {
            selectedTrackForMenu = null
        }
    }

    fun moveDraggingItem(originIndex: Int, dragAmountY: Float) {
        if (!state.isEditMode || displayTracks.isEmpty()) return
        dragOffsetY += dragAmountY
        var activeIndex = draggingIndex.takeIf { it >= 0 } ?: originIndex
        val itemHeight = (itemHeights[activeIndex] ?: itemHeights[originIndex] ?: 0).toFloat()
        if (itemHeight <= 0f) return
        val threshold = itemHeight / 2f

        while (dragOffsetY > threshold && activeIndex < displayTracks.lastIndex) {
            viewModel.moveEditingTrack(activeIndex, activeIndex + 1)
            activeIndex += 1
            dragOffsetY -= itemHeight
        }

        while (dragOffsetY < -threshold && activeIndex > 0) {
            viewModel.moveEditingTrack(activeIndex, activeIndex - 1)
            activeIndex -= 1
            dragOffsetY += itemHeight
        }

        draggingIndex = activeIndex
    }

    Column(modifier = Modifier.fillMaxSize()) {
        when {
            state.error != null -> {
                // Back button + error
                Box(modifier = Modifier.statusBarsPadding().padding(8.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
                ErrorView(
                    message = state.error!!,
                    onRetry = { viewModel.loadPlaylist(playlistId, platform, title, source) }
                )
            }
            state.isLoading -> {
                // Header skeleton + shimmer list
                Box(modifier = Modifier.statusBarsPadding().padding(8.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(10) { ShimmerMediaListItem() }
                }
            }
            else -> {
                val headerCoverUrl = state.coverUrl.ifBlank {
                    displayTracks.firstOrNull()?.coverUrl.orEmpty()
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Header with large cover + gradient
                    item(key = "header", contentType = "header") {
                        PlaylistHeader(
                            title = state.title.ifBlank { title },
                            coverUrl = headerCoverUrl,
                            trackCount = displayTracks.size,
                            isFavoritesCollection = state.isFavoritesCollection,
                            isFavoritesSelectionMode = state.isFavoritesSelectionMode,
                            selectedFavoritesCount = selectedFavoriteCount,
                            areAllFavoritesSelected = areAllFavoritesSelected,
                            isEditable = state.isLocalPlaylist,
                            isEditMode = state.isEditMode,
                            isSavingEdits = state.isSavingEdits,
                            isDeletingFavorites = state.isDeletingFavorites,
                            onBack = onBack,
                            onEdit = viewModel::enterEditMode,
                            onCancelEdit = viewModel::cancelEditMode,
                            onSaveEdit = viewModel::commitPlaylistEdits,
                            onEnterFavoritesSelection = viewModel::enterFavoritesSelectionMode,
                            onCancelFavoritesSelection = viewModel::cancelFavoritesSelectionMode,
                            onToggleSelectAllFavorites = viewModel::toggleSelectAllFavorites,
                            onDeleteSelectedFavorites = viewModel::deleteSelectedFavorites,
                            onPlayAll = {
                                if (displayTracks.isNotEmpty()) {
                                    playerViewModel.playTrack(displayTracks.first(), displayTracks, 0)
                                }
                            }
                        )
                    }

                    if (displayTracks.isEmpty() && !state.isEditMode) {
                        item(key = "empty_state", contentType = "empty") {
                            EmptyStateView(
                                icon = if (state.isFavoritesCollection) Icons.Default.Favorite else Icons.Default.PlayArrow,
                                title = if (state.isFavoritesCollection) "你还没收藏歌曲" else "这个歌单还是空的",
                                subtitle = if (state.isFavoritesCollection) {
                                    "看到喜欢的歌就点心心，回这儿就能慢慢盘。"
                                } else {
                                    "先加几首歌进来，这里才热闹。"
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 40.dp)
                            )
                        }
                    }

                    if (state.isEditMode) {
                        item(key = "edit_hint", contentType = "hint") {
                            EditModeHint(trackCount = displayTracks.size)
                        }
                    }

                    if (state.isFavoritesSelectionMode) {
                        item(key = "favorite_select_hint", contentType = "hint") {
                            FavoritesSelectionHint(trackCount = selectedFavoriteCount)
                        }
                    }

                    state.editMessage?.let { message ->
                        item(key = "edit_message", contentType = "message") {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Track list
                    itemsIndexed(
                        displayTracks,
                        key = { _, t -> "${t.platform.id}:${t.id}" },
                        contentType = { _, _ -> "track" }
                    ) { index, track ->
                        if (state.isEditMode) {
                            EditablePlaylistItem(
                                track = track,
                                index = index,
                                isDragging = index == draggingIndex,
                                dragOffsetY = if (index == draggingIndex) dragOffsetY else 0f,
                                onMeasured = { heightPx -> itemHeights[index] = heightPx },
                                onRemove = { viewModel.removeEditingTrack(track) },
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
                        } else if (state.isFavoritesSelectionMode) {
                            SelectableFavoriteItem(
                                track = track,
                                index = index,
                                selected = "${track.platform.id}:${track.id}" in state.selectedFavoriteKeys,
                                onToggle = { viewModel.toggleFavoriteSelection(track) }
                            )
                        } else {
                            MediaListItem(
                                track = track,
                                index = index,
                                onClick = {
                                    playerViewModel.playTrack(track, displayTracks, index)
                                },
                                onMoreClick = {
                                    selectedTrackForMenu = track
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (!state.isEditMode && !state.isFavoritesSelectionMode) {
        selectedTrackForMenu?.let { track ->
            TrackMoreMenu(
                onDismiss = { selectedTrackForMenu = null },
                onAddToPlaylist = {
                    selectedTrackForMenu = null
                    trackPendingPlaylistAddition = track
                },
                onDownload = { playerViewModel.downloadTrack(track) },
                onShare = { ShareUtils.shareTrack(context, track) }
            )
        }
    }

    trackPendingPlaylistAddition?.let { track ->
        AddTrackToPlaylistSheet(
            track = track,
            playerViewModel = playerViewModel,
            onDismiss = { trackPendingPlaylistAddition = null }
        )
    }
}

@Composable
private fun PlaylistHeader(
    title: String,
    coverUrl: String,
    trackCount: Int,
    isFavoritesCollection: Boolean,
    isFavoritesSelectionMode: Boolean,
    selectedFavoritesCount: Int,
    areAllFavoritesSelected: Boolean,
    isEditable: Boolean,
    isEditMode: Boolean,
    isSavingEdits: Boolean,
    isDeletingFavorites: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onEnterFavoritesSelection: () -> Unit,
    onCancelFavoritesSelection: () -> Unit,
    onToggleSelectAllFavorites: () -> Unit,
    onDeleteSelectedFavorites: () -> Unit,
    onPlayAll: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        // Background cover (blurred effect via low alpha + gradient)
        if (coverUrl.isNotEmpty()) {
            CoverImage(
                url = coverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.surface
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
                when {
                    isEditable -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isEditMode) {
                                HeaderActionButton(
                                    text = "取消",
                                    enabled = !isSavingEdits,
                                    onClick = onCancelEdit
                                )
                                HeaderActionButton(
                                    text = if (isSavingEdits) "保存中..." else "完成",
                                    enabled = !isSavingEdits,
                                    onClick = onSaveEdit
                                )
                            } else {
                                HeaderActionButton(
                                    text = "编辑",
                                    enabled = true,
                                    onClick = onEdit
                                )
                            }
                        }
                    }
                    isFavoritesCollection && trackCount > 0 -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isFavoritesSelectionMode) {
                                HeaderActionButton(
                                    text = "取消",
                                    enabled = !isDeletingFavorites,
                                    onClick = onCancelFavoritesSelection
                                )
                                HeaderActionButton(
                                    text = if (areAllFavoritesSelected) "清空" else "全选",
                                    enabled = !isDeletingFavorites,
                                    onClick = onToggleSelectAllFavorites
                                )
                                HeaderActionButton(
                                    text = if (isDeletingFavorites) {
                                        "删除中..."
                                    } else {
                                        "删除($selectedFavoritesCount)"
                                    },
                                    enabled = selectedFavoritesCount > 0 && !isDeletingFavorites,
                                    onClick = onDeleteSelectedFavorites
                                )
                            } else {
                                HeaderActionButton(
                                    text = "管理",
                                    enabled = true,
                                    onClick = onEnterFavoritesSelection
                                )
                            }
                        }
                    }
                }
            }

            // Center: cover + title
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (coverUrl.isNotEmpty()) {
                    CoverImage(
                        url = coverUrl,
                        contentDescription = title,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(AppShapes.Medium))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                } else if (isFavoritesCollection) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(AppShapes.Medium))
                            .background(Color.White.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFFF6B8A),
                            modifier = Modifier.size(52.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                Text(
                    text = when {
                        isFavoritesSelectionMode -> "已选择 ${selectedFavoritesCount} 首"
                        isEditMode -> "编辑模式 · ${trackCount}首歌曲"
                        isFavoritesCollection -> "我喜欢的歌 · ${trackCount}首"
                        else -> "${trackCount}首歌曲"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Play all button
            if (trackCount > 0 && !isEditMode && !isFavoritesSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = onPlayAll,
                        shape = RoundedCornerShape(AppShapes.Large),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "播放全部",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(
            text = text,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun EditModeHint(trackCount: Int) {
    Text(
        text = if (trackCount > 0) {
            "长按拖拽调整顺序，右侧按钮可移除歌曲，点“完成”后批量保存。"
        } else {
            "歌单已经空了，点“完成”保存修改。"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
private fun FavoritesSelectionHint(trackCount: Int) {
    Text(
        text = if (trackCount > 0) {
            "已选 $trackCount 首，点右上角“删除”可批量移出收藏。"
        } else {
            "点列表前面的勾选框，选择要删除的歌曲。"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
private fun SelectableFavoriteItem(
    track: Track,
    index: Int,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clickable(onClick = onToggle)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() }
        )
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        CoverImage(
            url = track.coverUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(AppShapes.Small))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun EditablePlaylistItem(
    track: Track,
    index: Int,
    isDragging: Boolean,
    dragOffsetY: Float,
    onMeasured: (Int) -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isDragging) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffsetY }
            .clip(RoundedCornerShape(AppShapes.Medium))
            .background(backgroundColor)
            .onSizeChanged { onMeasured(it.height) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = "长按拖拽排序",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
                }
        )
        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))

        if (track.coverUrl.isNotBlank()) {
            CoverImage(
                url = track.coverUrl,
                contentDescription = track.title,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(AppShapes.Small))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(AppShapes.Small))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        IconButton(
            onClick = onRemove,
            enabled = !isDragging
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "移除歌曲",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
