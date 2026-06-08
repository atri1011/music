package com.music.myapplication.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.core.common.ShareUtils
import com.music.myapplication.domain.model.LyricLine
import com.music.myapplication.domain.model.Track
import com.music.myapplication.ui.theme.AppShapes
import com.music.myapplication.ui.theme.rememberDominantColorState

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PlayerLyricsScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToArtist: ((String, String, String) -> Unit)? = null,
    onNavigateToAlbum: ((String, String, String, String, String) -> Unit)? = null,
    onNavigateToVideoPlayer: ((Track) -> Unit)? = null,
    onNavigateToEqualizer: (() -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    modifier: Modifier = Modifier
) {
    val staticState by playerViewModel.staticUiState.collectAsStateWithLifecycle()
    val progress by playerViewModel.progressState.collectAsStateWithLifecycle()
    val lyricsState by playerViewModel.lyricsUiState.collectAsStateWithLifecycle()
    val trackInfoState by playerViewModel.trackInfoState.collectAsStateWithLifecycle()
    val commentsState by playerViewModel.commentsUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentTrack = staticState.currentTrack
    var hasLoadedTrack by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSleepTimerPicker by remember { mutableStateOf(false) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSimilarTracksSheet by remember { mutableStateOf(false) }
    var trackPendingPlaylistAddition by remember { mutableStateOf<Track?>(null) }
    var coverQuickActionTrack by remember { mutableStateOf<Track?>(null) }
    var posterLyricLine by remember { mutableStateOf<LyricLine?>(null) }

    LaunchedEffect(Unit) {
        playerViewModel.showLyricsPanel()
    }

    LaunchedEffect(currentTrack?.id, currentTrack?.platform?.id) {
        posterLyricLine = null
        if (currentTrack != null) {
            hasLoadedTrack = true
        } else if (hasLoadedTrack) {
            onBack()
        }
    }

    if (currentTrack == null) return

    val currentLyricIndex = remember(lyricsState.lyrics, progress.positionMs) {
        LyricsParser.findCurrentIndex(lyricsState.lyrics, progress.positionMs)
    }

    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    val dominantColorState = rememberDominantColorState(coverUrl = currentTrack.coverUrl)
    val animatedDominantColor by animateColorAsState(
        targetValue = dominantColorState.dominantColor,
        animationSpec = tween(800),
        label = "lyricsDominantColor"
    )

    Box(modifier = modifier.fillMaxSize()) {
        BlurredCoverBackground(
            coverUrl = currentTrack.coverUrl,
            dominantColor = animatedDominantColor
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }

            PlayerLyricsTopBar(
                isCoverPage = pagerState.currentPage == 0,
                currentTrack = currentTrack,
                onShowMoreMenu = { showMoreMenu = true }
            )

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val isLandscape = isLandscapePlayerLayout(maxWidth = maxWidth, maxHeight = maxHeight)
                if (isLandscape) {
                    LandscapePlayerBody(
                        track = currentTrack,
                        staticState = staticState,
                        progress = progress,
                        lyricsState = lyricsState,
                        currentLyricIndex = currentLyricIndex,
                        onLyricLongPress = { posterLyricLine = it },
                        onCoverLongPress = { coverQuickActionTrack = currentTrack },
                        onOpenVideo = onNavigateToVideoPlayer?.let { { it(currentTrack) } },
                        onToggleFavorite = playerViewModel::toggleFavorite,
                        onShowComments = playerViewModel::showComments,
                        onSeek = playerViewModel::seekTo,
                        onToggleMode = playerViewModel::togglePlaybackMode,
                        onPlayPause = playerViewModel::togglePlayPause,
                        onPrevious = playerViewModel::skipPrevious,
                        onNext = playerViewModel::skipNext,
                        onOpenQueue = { showQueueSheet = true },
                        sharedTransitionScope = sharedTransitionScope
                    )
                } else {
                    PortraitPlayerBody(
                        track = currentTrack,
                        staticState = staticState,
                        progress = progress,
                        lyricsState = lyricsState,
                        trackInfoState = trackInfoState,
                        currentLyricIndex = currentLyricIndex,
                        pagerState = pagerState,
                        playerViewModel = playerViewModel,
                        onNavigateToAlbum = onNavigateToAlbum,
                        onLyricLongPress = { posterLyricLine = it },
                        onCoverLongPress = { coverQuickActionTrack = currentTrack },
                        onOpenVideo = onNavigateToVideoPlayer?.let { { it(currentTrack) } },
                        onToggleFavorite = playerViewModel::toggleFavorite,
                        onShowComments = playerViewModel::showComments,
                        onSeek = playerViewModel::seekTo,
                        onToggleMode = playerViewModel::togglePlaybackMode,
                        onPlayPause = playerViewModel::togglePlayPause,
                        onPrevious = playerViewModel::skipPrevious,
                        onNext = playerViewModel::skipNext,
                        onOpenQueue = { showQueueSheet = true },
                        sharedTransitionScope = sharedTransitionScope
                    )
                }
            }
        }

        if (commentsState.isVisible) {
            TrackCommentsSheet(
                state = commentsState,
                onDismiss = playerViewModel::hideComments,
                onRetry = playerViewModel::retryLoadComments,
                onSelectSort = playerViewModel::selectCommentSort
            )
        }

        if (showMoreMenu) {
            PlayerMoreMenu(
                onDismiss = { showMoreMenu = false },
                onSleepTimer = { showSleepTimerPicker = true },
                onVideoPlayer = { onNavigateToVideoPlayer?.invoke(currentTrack) },
                onAddToPlaylist = {
                    showMoreMenu = false
                    trackPendingPlaylistAddition = currentTrack
                },
                onShare = { ShareUtils.shareTrack(context, currentTrack) },
                onSpeedPicker = { showSpeedPicker = true },
                onEqualizer = { onNavigateToEqualizer?.invoke() },
                onSimilarTracks = { showSimilarTracksSheet = true },
                currentSpeed = staticState.speed
            )
        }

        if (showSimilarTracksSheet) {
            SimilarTracksSheet(
                currentTrack = currentTrack,
                trackInfoState = trackInfoState,
                onDismiss = { showSimilarTracksSheet = false },
                onPlayTrack = playerViewModel::playTrack
            )
        }

        coverQuickActionTrack?.let { track ->
            TrackMoreMenu(
                onDismiss = { coverQuickActionTrack = null },
                onToggleFavorite = {
                    coverQuickActionTrack = null
                    playerViewModel.toggleFavorite()
                },
                onAddToPlaylist = {
                    coverQuickActionTrack = null
                    trackPendingPlaylistAddition = track
                },
                onDownload = {
                    coverQuickActionTrack = null
                    playerViewModel.downloadTrack(track)
                },
                onArtist = onNavigateToArtist?.let {
                    {
                        coverQuickActionTrack = null
                        it(track.id, track.platform.id, track.artist)
                    }
                },
                onAlbum = if (track.albumId.isNotBlank() && onNavigateToAlbum != null) {
                    {
                        coverQuickActionTrack = null
                        onNavigateToAlbum(
                            track.albumId,
                            track.platform.id,
                            track.album,
                            track.artist,
                            track.coverUrl
                        )
                    }
                } else {
                    null
                },
                onShare = {
                    coverQuickActionTrack = null
                    ShareUtils.shareTrack(context, track)
                }
            )
        }

        if (showSleepTimerPicker) {
            SleepTimerPickerSheet(
                playerViewModel = playerViewModel,
                onDismiss = { showSleepTimerPicker = false }
            )
        }

        if (showSpeedPicker) {
            SpeedPickerSheet(
                currentSpeed = staticState.speed,
                onSpeedSelected = { playerViewModel.setSpeed(it) },
                onDismiss = { showSpeedPicker = false }
            )
        }

        if (showQueueSheet) {
            QueueSheet(
                playerViewModel = playerViewModel,
                onDismiss = { showQueueSheet = false }
            )
        }

        trackPendingPlaylistAddition?.let { track ->
            AddTrackToPlaylistSheet(
                track = track,
                playerViewModel = playerViewModel,
                onDismiss = { trackPendingPlaylistAddition = null }
            )
        }

        posterLyricLine?.let { lyricLine ->
            LyricsPosterDialog(
                track = currentTrack,
                lyricLine = lyricLine,
                onDismiss = { posterLyricLine = null }
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PortraitPlayerBody(
    track: Track,
    staticState: PlayerStaticUiState,
    progress: PlaybackProgressUiState,
    lyricsState: LyricsUiState,
    trackInfoState: TrackInfoUiState,
    currentLyricIndex: Int,
    pagerState: PagerState,
    playerViewModel: PlayerViewModel,
    onNavigateToAlbum: ((String, String, String, String, String) -> Unit)?,
    onLyricLongPress: (LyricLine) -> Unit,
    onCoverLongPress: () -> Unit,
    onOpenVideo: (() -> Unit)?,
    onToggleFavorite: () -> Unit,
    onShowComments: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleMode: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            when (page) {
                0 -> CoverPage(
                    track = track,
                    isPlaying = staticState.isPlaying,
                    lyrics = lyricsState.lyrics,
                    currentIndex = currentLyricIndex,
                    onPreviousTrack = onPrevious,
                    onNextTrack = onNext,
                    onCoverLongPress = onCoverLongPress,
                    sharedTransitionScope = sharedTransitionScope
                )
                1 -> LyricsPanelContent(
                    lyricsState = lyricsState,
                    currentIndex = currentLyricIndex,
                    onLyricLongPress = onLyricLongPress,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                )
                2 -> SongInfoPage(
                    track = track,
                    trackInfoState = trackInfoState,
                    playerViewModel = playerViewModel,
                    onNavigateToAlbum = onNavigateToAlbum
                )
            }
        }

        PagerIndicator(
            pageCount = 3,
            currentPage = pagerState.currentPage,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        )

        PlayerLyricsControlStack(
            track = track,
            staticState = staticState,
            progress = progress,
            onOpenVideo = onOpenVideo,
            onToggleFavorite = onToggleFavorite,
            onShowComments = onShowComments,
            onSeek = onSeek,
            onToggleMode = onToggleMode,
            onPlayPause = onPlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onOpenQueue = onOpenQueue,
            modifier = Modifier.fillMaxWidth(),
            horizontalPadding = 24.dp
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun LandscapePlayerBody(
    track: Track,
    staticState: PlayerStaticUiState,
    progress: PlaybackProgressUiState,
    lyricsState: LyricsUiState,
    currentLyricIndex: Int,
    onLyricLongPress: (LyricLine) -> Unit,
    onCoverLongPress: () -> Unit,
    onOpenVideo: (() -> Unit)?,
    onToggleFavorite: () -> Unit,
    onShowComments: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleMode: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverPage(
            track = track,
            isPlaying = staticState.isPlaying,
            lyrics = lyricsState.lyrics,
            currentIndex = currentLyricIndex,
            onPreviousTrack = onPrevious,
            onNextTrack = onNext,
            onCoverLongPress = onCoverLongPress,
            sharedTransitionScope = sharedTransitionScope,
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight(),
            artworkSize = 170.dp,
            showLyricsPreview = false,
            compactLayout = true
        )

        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LyricsPanelContent(
                lyricsState = lyricsState,
                currentIndex = currentLyricIndex,
                onLyricLongPress = onLyricLongPress,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppShapes.Large))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            )

            PlayerLyricsControlStack(
                track = track,
                staticState = staticState,
                progress = progress,
                onOpenVideo = onOpenVideo,
                onToggleFavorite = onToggleFavorite,
                onShowComments = onShowComments,
                onSeek = onSeek,
                onToggleMode = onToggleMode,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onOpenQueue = onOpenQueue,
                modifier = Modifier.fillMaxWidth(),
                horizontalPadding = 0.dp,
                compact = true
            )
        }
    }
}

@Composable
private fun PlayerLyricsControlStack(
    track: Track,
    staticState: PlayerStaticUiState,
    progress: PlaybackProgressUiState,
    onOpenVideo: (() -> Unit)?,
    onToggleFavorite: () -> Unit,
    onShowComments: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleMode: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 24.dp,
    compact: Boolean = false
) {
    Column(modifier = modifier) {
        SecondaryActionRow(
            trackKey = "${track.platform.id}:${track.id}",
            isFavorite = track.isFavorite,
            onOpenVideo = onOpenVideo,
            onToggleFavorite = onToggleFavorite,
            onShowComments = onShowComments,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
        )

        Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))

        PlayerProgressSection(
            progress = progress,
            onSeek = onSeek,
            modifier = Modifier.padding(horizontal = horizontalPadding)
        )

        Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))

        TransportControlRow(
            isPlaying = staticState.isPlaying,
            playbackMode = staticState.playbackMode,
            onToggleMode = onToggleMode,
            onPlayPause = onPlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onOpenQueue = onOpenQueue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
        )
    }
}

private fun isLandscapePlayerLayout(maxWidth: Dp, maxHeight: Dp): Boolean =
    maxWidth > maxHeight && maxWidth >= 560.dp
