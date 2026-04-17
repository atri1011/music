package com.music.myapplication.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.core.common.ShareUtils
import com.music.myapplication.domain.model.LyricLine
import com.music.myapplication.domain.model.Track

@Composable
fun PlayerLyricsScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: ((String, String, String, String, String) -> Unit)? = null,
    onNavigateToVideoPlayer: ((Track) -> Unit)? = null,
    onNavigateToEqualizer: (() -> Unit)? = null,
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
    var trackPendingPlaylistAddition by remember { mutableStateOf<Track?>(null) }
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

    Box(modifier = modifier.fillMaxSize()) {
        BlurredCoverBackground(coverUrl = currentTrack.coverUrl)

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

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> CoverPage(
                        track = currentTrack,
                        isPlaying = staticState.isPlaying,
                        lyrics = lyricsState.lyrics,
                        currentIndex = currentLyricIndex
                    )
                    1 -> LyricsPanelContent(
                        lyricsState = lyricsState,
                        currentIndex = currentLyricIndex,
                        onLyricLongPress = { posterLyricLine = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp)
                    )
                    2 -> SongInfoPage(
                        track = currentTrack,
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

            SecondaryActionRow(
                trackKey = "${currentTrack.platform.id}:${currentTrack.id}",
                isFavorite = currentTrack.isFavorite,
                onOpenVideo = onNavigateToVideoPlayer?.let { { it(currentTrack) } },
                onToggleFavorite = playerViewModel::toggleFavorite,
                onShowComments = playerViewModel::showComments,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            PlayerProgressSection(
                progress = progress,
                onSeek = playerViewModel::seekTo,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            TransportControlRow(
                isPlaying = staticState.isPlaying,
                playbackMode = staticState.playbackMode,
                onToggleMode = playerViewModel::togglePlaybackMode,
                onPlayPause = playerViewModel::togglePlayPause,
                onPrevious = playerViewModel::skipPrevious,
                onNext = playerViewModel::skipNext,
                onOpenQueue = { showQueueSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
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
                currentSpeed = staticState.speed
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
