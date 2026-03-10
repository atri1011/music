package com.music.myapplication.media.service

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.EqualizerPreferences
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.feature.player.state.SleepTimerStateHolder
import com.music.myapplication.feature.player.state.TrackPlaybackResolver
import com.music.myapplication.media.equalizer.EqualizerManager
import com.music.myapplication.media.player.PlaybackModeManager
import com.music.myapplication.media.player.QueueManager
import com.music.myapplication.media.session.PlaybackLoadRequest
import com.music.myapplication.media.session.loadTrackSessionCommand
import com.music.myapplication.media.session.toPlaybackLoadRequestOrNull
import com.music.myapplication.media.state.PlaybackStateStore
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MusicPlaybackService : MediaLibraryService() {

    @Inject lateinit var exoPlayer: ExoPlayer
    @Inject lateinit var stateStore: PlaybackStateStore
    @Inject lateinit var queueManager: QueueManager
    @Inject lateinit var modeManager: PlaybackModeManager
    @Inject lateinit var sleepTimer: SleepTimerStateHolder
    @Inject lateinit var equalizerManager: EqualizerManager
    @Inject lateinit var equalizerPreferences: EqualizerPreferences
    @Inject lateinit var playerPreferences: PlayerPreferences
    @Inject lateinit var trackPlaybackResolver: TrackPlaybackResolver
    @Inject lateinit var localLibraryRepository: LocalLibraryRepository

    private var mediaSession: MediaLibraryService.MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionUpdateJob: Job? = null
    private var equalizerSettingsJob: Job? = null
    private var playerSettingsJob: Job? = null
    private var transitionJob: Job? = null
    private val libraryTrackCache = LinkedHashMap<String, Track>()

    private var cachedEqEnabled = false
    private var cachedEqPresetIndex = 0
    private var cachedEqCustomBands: Map<Int, Int> = emptyMap()
    private var cachedQuality = "128k"
    private var cachedCrossfadeEnabled = false
    private var cachedCrossfadeDurationMs = PlayerPreferences.DEFAULT_CROSSFADE_DURATION_MS

    private companion object {
        const val ROOT_ID = "library_root"
        const val FAVORITES_ID = "favorites"
        const val RECENTS_ID = "recent_plays"
        const val PLAYLISTS_ID = "playlists"
        const val PLAYLIST_PREFIX = "playlist:"
        const val TRACK_PREFIX = "track:"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        exoPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )

        exoPlayer.addListener(playerListener)

        mediaSession = MediaLibraryService.MediaLibrarySession.Builder(this, exoPlayer, sessionCallback)
            .build()

        stateStore.updateSpeed(exoPlayer.playbackParameters.speed)
        bindEqualizerToAudioSession(exoPlayer.audioSessionId)
        observeEqualizerSettings()
        observePlayerSettings()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaLibraryService.MediaLibrarySession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        positionUpdateJob?.cancel()
        equalizerSettingsJob?.cancel()
        playerSettingsJob?.cancel()
        transitionJob?.cancel()
        equalizerManager.release()
        setPlayerVolume(1f)
        mediaSession?.run {
            player.removeListener(playerListener)
            player.stop()
            player.clearMediaItems()
            release()
        }
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = serviceScope.launch {
            while (isActive) {
                stateStore.updatePosition(exoPlayer.currentPosition)
                delay(500)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun observePlayerSettings() {
        playerSettingsJob?.cancel()
        playerSettingsJob = serviceScope.launch {
            combine(
                playerPreferences.quality,
                playerPreferences.crossfadeEnabled,
                playerPreferences.crossfadeDurationMs
            ) { quality, crossfadeEnabled, crossfadeDurationMs ->
                Triple(quality, crossfadeEnabled, crossfadeDurationMs)
            }.collect { (quality, crossfadeEnabled, crossfadeDurationMs) ->
                cachedQuality = quality
                cachedCrossfadeEnabled = crossfadeEnabled
                cachedCrossfadeDurationMs = crossfadeDurationMs
                if (!crossfadeEnabled) {
                    cancelActiveTransition()
                }
            }
        }
    }

    private fun bindEqualizerToAudioSession(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) {
            equalizerManager.release()
            return
        }
        equalizerManager.bindToAudioSession(audioSessionId)
        applyEqualizerSettings()
    }

    private fun observeEqualizerSettings() {
        equalizerSettingsJob?.cancel()
        equalizerSettingsJob = serviceScope.launch {
            combine(
                equalizerPreferences.enabled,
                equalizerPreferences.presetIndex,
                equalizerPreferences.customBandLevels
            ) { enabled, presetIndex, customBands ->
                Triple(enabled, presetIndex, customBands)
            }.collect { (enabled, presetIndex, customBands) ->
                cachedEqEnabled = enabled
                cachedEqPresetIndex = presetIndex
                cachedEqCustomBands = customBands
                applyEqualizerSettings()
            }
        }
    }

    private fun applyEqualizerSettings() {
        equalizerManager.setEnabled(cachedEqEnabled)
        if (!cachedEqEnabled) return
        if (cachedEqPresetIndex >= 0) {
            equalizerManager.setPreset(cachedEqPresetIndex)
        } else {
            equalizerManager.setBandLevels(cachedEqCustomBands)
        }
    }

    private val sessionCallback = object : MediaLibraryService.MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val defaultResult = super.onConnect(session, controller)
            val sessionCommands = defaultResult.availableSessionCommands
                .buildUpon()
                .add(loadTrackSessionCommand)
                .build()
            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                defaultResult.availablePlayerCommands
            )
        }

        override fun onGetLibraryRoot(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(
                LibraryResult.ofItem(buildRootItem(), params)
            )
        }

        override fun onGetChildren(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {
            val children = when {
                parentId == ROOT_ID -> buildRootChildren()
                parentId == FAVORITES_ID -> buildFavoriteChildren()
                parentId == RECENTS_ID -> buildRecentChildren()
                parentId == PLAYLISTS_ID -> buildPlaylistChildren()
                parentId.startsWith(PLAYLIST_PREFIX) -> buildPlaylistTrackChildren(
                    playlistId = parentId.removePrefix(PLAYLIST_PREFIX)
                )
                else -> emptyList()
            }
            return Futures.immediateFuture(
                LibraryResult.ofItemList(children, params)
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolvedItems = mediaItems.map { mediaItem ->
                resolvePlayableMediaItem(mediaItem)
            }.toMutableList()
            return Futures.immediateFuture(resolvedItems)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            return when (customCommand.customAction) {
                loadTrackSessionCommand.customAction -> {
                    val request = args.toPlaybackLoadRequestOrNull()
                    if (request == null) {
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                    } else {
                        handleLoadTrackRequest(request)
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                }
                else -> super.onCustomCommand(session, controller, customCommand, args)
            }
        }
    }

    private fun buildRootItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("My Application")
                    .setSubtitle("Android Auto")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()
    }

    private fun buildRootChildren(): List<MediaItem> {
        return listOf(
            buildFolderItem(
                mediaId = FAVORITES_ID,
                title = "收藏",
                subtitle = "你常听和收下来的歌",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
            ),
            buildFolderItem(
                mediaId = RECENTS_ID,
                title = "最近播放",
                subtitle = "接着上次继续听",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
            ),
            buildFolderItem(
                mediaId = PLAYLISTS_ID,
                title = "歌单",
                subtitle = "本地资料库歌单",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
            )
        )
    }

    private fun buildFavoriteChildren(): List<MediaItem> = runBlocking {
        localLibraryRepository.getFavorites().first().map(::buildTrackItem)
    }

    private fun buildRecentChildren(): List<MediaItem> = runBlocking {
        localLibraryRepository.getRecentPlays(limit = 30).first().map(::buildTrackItem)
    }

    private fun buildPlaylistChildren(): List<MediaItem> = runBlocking {
        val playlists = localLibraryRepository.getPlaylists().first()
        playlists.map { playlist ->
            buildFolderItem(
                mediaId = "$PLAYLIST_PREFIX${playlist.id}",
                title = playlist.name,
                subtitle = "${playlist.trackCount} 首",
                mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
                artworkUrl = playlist.coverUrl
            )
        }
    }

    private fun buildPlaylistTrackChildren(playlistId: String): List<MediaItem> = runBlocking {
        localLibraryRepository.getPlaylistSongs(playlistId).first().map(::buildTrackItem)
    }

    private fun buildFolderItem(
        mediaId: String,
        title: String,
        subtitle: String,
        mediaType: Int,
        artworkUrl: String = ""
    ): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(mediaType)
        artworkUrl.takeIf { it.isNotBlank() }?.let { url ->
            metadataBuilder.setArtworkUri(Uri.parse(url))
        }
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun buildTrackItem(track: Track): MediaItem {
        val mediaId = trackMediaId(track)
        libraryTrackCache[mediaId] = track
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        track.coverUrl.takeIf { it.isNotBlank() }?.let { url ->
            metadataBuilder.setArtworkUri(Uri.parse(url))
        }
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun resolvePlayableMediaItem(mediaItem: MediaItem): MediaItem {
        val cachedTrack = libraryTrackCache[mediaItem.mediaId]
        if (cachedTrack == null || cachedTrack.playableUrl.isNotBlank()) {
            return mediaItem
        }

        val resolvedTrack = runBlocking(Dispatchers.IO) {
            when (val result = trackPlaybackResolver.resolve(cachedTrack, cachedQuality)) {
                is Result.Success -> result.data
                is Result.Error,
                Result.Loading -> null
            }
        } ?: return mediaItem

        libraryTrackCache[mediaItem.mediaId] = resolvedTrack
        return mediaItem.buildUpon()
            .setUri(resolvedTrack.playableUrl)
            .setMediaMetadata(
                mediaItem.mediaMetadata.buildUpon()
                    .setTitle(resolvedTrack.title)
                    .setArtist(resolvedTrack.artist)
                    .setAlbumTitle(resolvedTrack.album)
                    .build()
            )
            .build()
    }

    private fun trackMediaId(track: Track): String = "$TRACK_PREFIX${track.platform.id}:${track.id}"

    private fun handleLoadTrackRequest(request: PlaybackLoadRequest) {
        queueManager.setQueue(request.queue, request.index)
        if (queueManager.currentIndex >= 0) {
            queueManager.updateTrack(queueManager.currentIndex, request.track)
        }
        stateStore.updateTrack(request.track)
        stateStore.updateQueue(queueManager.queue, queueManager.currentIndex)
        stateStore.updatePosition(request.startPositionMs.coerceAtLeast(0L))
        stateStore.updateDuration(request.track.durationMs.coerceAtLeast(0L))
        if (!request.autoPlay) {
            stateStore.updatePlaying(false)
        }
        if (request.track.playableUrl.isBlank()) return

        launchTransition {
            loadTrackOnPlayer(
                track = request.track,
                autoPlay = request.autoPlay,
                startPositionMs = request.startPositionMs,
                transitionMode = if (shouldUseCrossfade(request.autoPlay)) {
                    CrossfadeTransitionMode.FADE_THROUGH
                } else {
                    CrossfadeTransitionMode.DIRECT
                }
            )
        }
    }

    private fun launchTransition(block: suspend () -> Unit) {
        cancelActiveTransition()
        val job = serviceScope.launch { block() }
        transitionJob = job
        job.invokeOnCompletion {
            if (transitionJob === job) {
                transitionJob = null
            }
        }
    }

    private fun cancelActiveTransition() {
        transitionJob?.cancel()
        transitionJob = null
        setPlayerVolume(1f)
    }

    private fun shouldUseCrossfade(autoPlay: Boolean): Boolean =
        autoPlay &&
            cachedCrossfadeEnabled &&
            exoPlayer.isPlaying &&
            exoPlayer.mediaItemCount > 0

    private suspend fun loadTrackOnPlayer(
        track: Track,
        autoPlay: Boolean,
        startPositionMs: Long,
        transitionMode: CrossfadeTransitionMode
    ) {
        val mediaItem = MediaItem.fromUri(track.playableUrl)
        try {
            when (transitionMode) {
                CrossfadeTransitionMode.FADE_THROUGH -> fadePlayerVolumeTo(
                    targetVolume = 0f,
                    durationMs = cachedCrossfadeDurationMs
                )
                CrossfadeTransitionMode.DIRECT,
                CrossfadeTransitionMode.FADE_IN_ONLY -> Unit
            }

            if (autoPlay && transitionMode != CrossfadeTransitionMode.DIRECT) {
                setPlayerVolume(0f)
            } else {
                setPlayerVolume(1f)
            }

            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            if (startPositionMs > 0L) {
                exoPlayer.seekTo(startPositionMs)
            }
            if (autoPlay) {
                exoPlayer.play()
            } else {
                exoPlayer.pause()
            }

            if (autoPlay && transitionMode != CrossfadeTransitionMode.DIRECT) {
                fadePlayerVolumeTo(
                    targetVolume = 1f,
                    durationMs = cachedCrossfadeDurationMs
                )
            } else {
                setPlayerVolume(1f)
            }
        } catch (cancelled: CancellationException) {
            setPlayerVolume(1f)
            throw cancelled
        } catch (_: IllegalStateException) {
            setPlayerVolume(1f)
        }
    }

    private suspend fun fadePlayerVolumeTo(targetVolume: Float, durationMs: Int) {
        if (!exoPlayer.isCommandAvailable(Player.COMMAND_SET_VOLUME)) return

        val startVolume = exoPlayer.volume
        val boundedTarget = targetVolume.coerceIn(0f, 1f)
        if (durationMs <= 0 || startVolume == boundedTarget) {
            setPlayerVolume(boundedTarget)
            return
        }

        val steps = (durationMs / 40f).roundToInt().coerceAtLeast(1)
        val delayPerStep = (durationMs / steps.toLong()).coerceAtLeast(1L)
        repeat(steps) { index ->
            val progress = (index + 1) / steps.toFloat()
            val nextVolume = startVolume + (boundedTarget - startVolume) * progress
            setPlayerVolume(nextVolume)
            delay(delayPerStep)
        }
        setPlayerVolume(boundedTarget)
    }

    private fun setPlayerVolume(volume: Float) {
        if (!exoPlayer.isCommandAvailable(Player.COMMAND_SET_VOLUME)) return
        exoPlayer.volume = volume.coerceIn(0f, 1f)
    }

    private fun handlePlaybackEnded() {
        if (sleepTimer.shouldPauseAfterCurrentTrack()) {
            sleepTimer.handleCurrentTrackEnded()
            stateStore.updatePlaying(false)
            return
        }

        val previousIndex = queueManager.currentIndex
        val nextTrack = modeManager.getNextTrack() ?: run {
            stateStore.updatePlaying(false)
            return
        }

        launchTransition {
            when (val result = withContext(Dispatchers.IO) {
                trackPlaybackResolver.resolve(nextTrack, cachedQuality)
            }) {
                is Result.Success -> {
                    val playable = result.data
                    queueManager.updateTrack(queueManager.currentIndex, playable)
                    stateStore.updateTrack(playable)
                    stateStore.updateQueue(queueManager.queue, queueManager.currentIndex)
                    stateStore.updatePosition(0L)
                    stateStore.updateDuration(playable.durationMs.coerceAtLeast(0L))
                    loadTrackOnPlayer(
                        track = playable,
                        autoPlay = true,
                        startPositionMs = 0L,
                        transitionMode = if (cachedCrossfadeEnabled) {
                            CrossfadeTransitionMode.FADE_IN_ONLY
                        } else {
                            CrossfadeTransitionMode.DIRECT
                        }
                    )
                    withContext(Dispatchers.IO) {
                        localLibraryRepository.recordRecentPlay(playable)
                    }
                }
                is Result.Error,
                Result.Loading -> {
                    if (previousIndex >= 0) {
                        queueManager.moveToIndex(previousIndex)
                    }
                    stateStore.updateQueue(queueManager.queue, queueManager.currentIndex)
                    stateStore.updatePlaying(false)
                }
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            stateStore.updatePlaying(isPlaying)
            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            stateStore.updateDuration(exoPlayer.duration.coerceAtLeast(0))
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    stateStore.updateDuration(exoPlayer.duration.coerceAtLeast(0))
                    bindEqualizerToAudioSession(exoPlayer.audioSessionId)
                }
                Player.STATE_ENDED -> {
                    handlePlaybackEnded()
                }
                else -> {}
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) {
                cancelActiveTransition()
            }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            stateStore.updateSpeed(playbackParameters.speed)
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            bindEqualizerToAudioSession(audioSessionId)
        }
    }

    private enum class CrossfadeTransitionMode {
        DIRECT,
        FADE_IN_ONLY,
        FADE_THROUGH
    }
}
