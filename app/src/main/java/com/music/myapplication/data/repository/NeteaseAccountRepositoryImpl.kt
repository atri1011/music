package com.music.myapplication.data.repository

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.database.dao.FavoritesDao
import com.music.myapplication.core.database.dao.PlaylistRemoteMapDao
import com.music.myapplication.core.database.dao.PlaylistSongsDao
import com.music.myapplication.core.database.dao.PlaylistsDao
import com.music.myapplication.core.database.entity.PlaylistEntity
import com.music.myapplication.core.database.entity.PlaylistRemoteMapEntity
import com.music.myapplication.core.database.entity.PlaylistSongEntity
import com.music.myapplication.core.database.mapper.toFavoriteEntity
import com.music.myapplication.core.database.mapper.toPlaylistSongEntity
import com.music.myapplication.core.database.mapper.toTrack
import com.music.myapplication.core.datastore.NeteaseAccountStore
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.core.network.retrofit.NeteaseCloudApiEnhancedApi
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.domain.model.NeteaseAccountSession
import com.music.myapplication.domain.model.NeteaseQrLoginPayload
import com.music.myapplication.domain.model.NeteaseQrLoginState
import com.music.myapplication.domain.model.NeteaseQrLoginStatus
import com.music.myapplication.domain.model.NeteaseSyncSummary
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.NeteaseAccountRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.UUID

@Singleton
class NeteaseAccountRepositoryImpl @Inject constructor(
    private val preferences: PlayerPreferences,
    private val accountStore: NeteaseAccountStore,
    private val enhancedApi: NeteaseCloudApiEnhancedApi,
    private val tuneHubApi: TuneHubApi,
    private val onlineRepo: OnlineMusicRepository,
    private val playlistsDao: PlaylistsDao,
    private val playlistSongsDao: PlaylistSongsDao,
    private val playlistRemoteMapDao: PlaylistRemoteMapDao,
    private val favoritesDao: FavoritesDao
) : NeteaseAccountRepository {

    private val syncMutex = Mutex()

    override val session: Flow<NeteaseAccountSession?> = accountStore.session

    override val isConfigured: Flow<Boolean> = preferences.neteaseCloudApiBaseUrl
        .map { it.isNotBlank() }

    override suspend fun refreshLoginStatus(): Result<NeteaseAccountSession?> {
        val savedSession = accountStore.session.first() ?: return Result.Success(null)
        val baseUrl = preferences.neteaseCloudApiBaseUrl.first()
        if (baseUrl.isBlank()) return Result.Error(AppError.Api(message = "请先设置网易云增强版接口地址"))
        return try {
            val response = enhancedApi.loginStatus(
                url = buildEndpoint(baseUrl, "login/status"),
                cookie = savedSession.cookie,
                realIp = DEFAULT_REAL_IP,
                timestamp = now()
            )
            val code = response.codeOrDefault()
            if (code != 200) {
                if (response.shouldClearSavedSession()) {
                    accountStore.clearSession()
                    Result.Success(null)
                } else {
                    recoverSessionFromPlaylistProbe(
                        baseUrl = baseUrl,
                        savedSession = savedSession,
                        originalError = AppError.Api(
                            message = response.messageOrDefault("登录状态校验失败"),
                            code = code
                        )
                    )
                }
            } else {
                val refreshed = response.toAccountSession(cookieOverride = savedSession.cookie)
                    ?.copy(lastSyncAt = savedSession.lastSyncAt)
                    ?: savedSession
                accountStore.saveSession(refreshed)
                Result.Success(refreshed)
            }
        } catch (e: Exception) {
            recoverSessionFromPlaylistProbe(
                baseUrl = baseUrl,
                savedSession = savedSession,
                originalError = AppError.Network(cause = e)
            )
        }
    }

    override suspend fun loginWithPassword(phone: String, password: String): Result<NeteaseAccountSession> {
        return authenticateCellphone(phone = phone, password = password, captcha = null)
    }

    override suspend fun sendCaptcha(phone: String): Result<Unit> {
        val baseUrl = preferences.neteaseCloudApiBaseUrl.first()
        if (baseUrl.isBlank()) return Result.Error(AppError.Api(message = "请先设置网易云增强版接口地址"))
        return try {
            val response = enhancedApi.sendCaptcha(
                url = buildEndpoint(baseUrl, "captcha/sent"),
                phone = phone,
                realIp = DEFAULT_REAL_IP,
                timestamp = now()
            )
            if (response.codeOrDefault() == 200) {
                Result.Success(Unit)
            } else {
                Result.Error(
                    AppError.Api(
                        message = response.messageOrDefault("验证码发送失败"),
                        code = response.codeOrDefault()
                    )
                )
            }
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    override suspend fun loginWithCaptcha(phone: String, captcha: String): Result<NeteaseAccountSession> {
        return authenticateCellphone(phone = phone, password = null, captcha = captcha)
    }

    override suspend fun createQrLogin(): Result<NeteaseQrLoginPayload> {
        val baseUrl = preferences.neteaseCloudApiBaseUrl.first()
        if (baseUrl.isBlank()) return Result.Error(AppError.Api(message = "请先设置网易云增强版接口地址"))
        return try {
            val keyResponse = enhancedApi.loginQrKey(
                url = buildEndpoint(baseUrl, "login/qr/key"),
                realIp = DEFAULT_REAL_IP,
                timestamp = now()
            )
            val key = keyResponse.jsonObjectOrNull()?.jsonObjectAt("data")?.string("unikey")
                ?: return Result.Error(AppError.Parse(message = "二维码 key 解析失败"))
            val qrResponse = enhancedApi.loginQrCreate(
                url = buildEndpoint(baseUrl, "login/qr/create"),
                key = key,
                realIp = DEFAULT_REAL_IP,
                timestamp = now()
            )
            if (qrResponse.codeOrDefault() != 200) {
                return Result.Error(
                    AppError.Api(
                        message = qrResponse.messageOrDefault("二维码生成失败"),
                        code = qrResponse.codeOrDefault()
                    )
                )
            }
            val data = qrResponse.jsonObjectOrNull()?.jsonObjectAt("data")
            Result.Success(
                NeteaseQrLoginPayload(
                    key = key,
                    qrImageUrl = data?.string("qrimg").orEmpty(),
                    qrUrl = data?.string("qrurl").orEmpty()
                )
            )
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    override suspend fun checkQrLogin(key: String): Result<NeteaseQrLoginStatus> {
        val baseUrl = preferences.neteaseCloudApiBaseUrl.first()
        if (baseUrl.isBlank()) return Result.Error(AppError.Api(message = "请先设置网易云增强版接口地址"))
        return try {
            val response = enhancedApi.loginQrCheck(
                url = buildEndpoint(baseUrl, "login/qr/check"),
                key = key,
                realIp = DEFAULT_REAL_IP,
                timestamp = now()
            )
            when (val code = response.codeOrDefault()) {
                801 -> Result.Success(
                    NeteaseQrLoginStatus(
                        state = NeteaseQrLoginState.WAITING,
                        message = response.messageOrDefault("等待扫码")
                    )
                )
                802 -> Result.Success(
                    NeteaseQrLoginStatus(
                        state = NeteaseQrLoginState.SCANNED,
                        message = response.messageOrDefault("已扫码，请在网易云确认登录")
                    )
                )
                803 -> {
                    val session = response.toAccountSession()
                        ?: return Result.Error(AppError.Parse(message = "扫码登录成功，但账号信息解析失败"))
                    accountStore.saveSession(session)
                    Result.Success(
                        NeteaseQrLoginStatus(
                            state = NeteaseQrLoginState.AUTHORIZED,
                            message = "登录成功",
                            session = session
                        )
                    )
                }
                800 -> Result.Success(
                    NeteaseQrLoginStatus(
                        state = NeteaseQrLoginState.EXPIRED,
                        message = response.messageOrDefault("二维码已过期，请重新生成")
                    )
                )
                else -> Result.Error(
                    AppError.Api(
                        message = response.messageOrDefault("二维码状态检查失败"),
                        code = code
                    )
                )
            }
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    override suspend fun syncLocalLibrary(): Result<NeteaseSyncSummary> = syncMutex.withLock {
        val baseUrl = preferences.neteaseCloudApiBaseUrl.first()
        if (baseUrl.isBlank()) {
            return@withLock Result.Error(AppError.Api(message = "请先设置网易云增强版接口地址"))
        }
        val currentSession = accountStore.session.first()
            ?: return@withLock Result.Error(AppError.Api(message = "请先登录网易云账号"))
        val ownerUid = currentSession.userId.toString()
        try {
            val playlistResponse = requestWithRateLimitRetry {
                enhancedApi.userPlaylist(
                    url = buildEndpoint(baseUrl, "user/playlist"),
                    uid = currentSession.userId,
                    cookie = currentSession.cookie,
                    realIp = DEFAULT_REAL_IP,
                    timestamp = now()
                )
            }
            if (playlistResponse.codeOrDefault() != 200) {
                return@withLock Result.Error(
                    AppError.Api(
                        message = playlistResponse.messageOrDefault("获取网易云歌单失败"),
                        code = playlistResponse.codeOrDefault()
                    )
                )
            }

            val remotePlaylists = playlistResponse.jsonObjectOrNull()?.jsonArrayAt("playlist")
                ?.mapNotNull { it.jsonObjectOrNull()?.toRemotePlaylist() }
                .orEmpty()
            val likedPlaylistRemoteId = remotePlaylists
                .firstOrNull { it.isLikedSongsPlaylist() }
                ?.id

            remotePlaylists.forEachIndexed { index, remotePlaylist ->
                syncRemotePlaylistWithRetry(
                    baseUrl = baseUrl,
                    session = currentSession,
                    remotePlaylist = remotePlaylist,
                    remoteOrder = index
                )
                if (index < remotePlaylists.lastIndex) {
                    delay(NETEASE_PLAYLIST_SYNC_INTERVAL_MS)
                }
            }

            val likeListResponse = requestWithRateLimitRetry {
                enhancedApi.likeList(
                    url = buildEndpoint(baseUrl, "likelist"),
                    uid = currentSession.userId,
                    cookie = currentSession.cookie,
                    realIp = DEFAULT_REAL_IP,
                    timestamp = now()
                )
            }
            if (likeListResponse.codeOrDefault() != 200) {
                return@withLock Result.Error(
                    AppError.Api(
                        message = likeListResponse.messageOrDefault("获取喜欢音乐失败"),
                        code = likeListResponse.codeOrDefault()
                    )
                )
            }

            val likedSongIds = likeListResponse.jsonObjectOrNull()?.jsonArrayAt("ids")
                ?.mapNotNull { it.jsonPrimitiveOrNull()?.contentOrNull }
                ?.distinct()
                .orEmpty()
            val likedSongIdSet = likedSongIds.toHashSet()
            val localNeteaseFavoriteIds = favoritesDao.getSongIdsByPlatform(Platform.NETEASE.id)
                .toHashSet()
            val newlyLikedSongIds = likedSongIds.filterNot(localNeteaseFavoriteIds::contains)
            val newlyLikedSongIdSet = newlyLikedSongIds.toHashSet()
            val metadataRepairSongIds = favoritesDao
                .getSongIdsWithMissingMetadata(Platform.NETEASE.id)
                .filter { it in likedSongIdSet }
            val songIdsToSync = (newlyLikedSongIds + metadataRepairSongIds).distinct()
            val syncedTracks = fetchNeteaseTracksByIds(songIdsToSync)
            syncedTracks.forEach { favoritesDao.insert(it.copy(isFavorite = true).toFavoriteEntity()) }
            val favoriteOrder = resolveNeteaseFavoritesOrder(
                ownerUid = ownerUid,
                likedPlaylistRemoteId = likedPlaylistRemoteId,
                likedSongIds = likedSongIds
            )
            alignNeteaseFavoritesOrder(favoriteOrder)
            val syncedNewFavoriteCount = syncedTracks.count { it.id in newlyLikedSongIdSet }

            val updatedSession = currentSession.copy(lastSyncAt = now())
            accountStore.saveSession(updatedSession)
            Result.Success(
                NeteaseSyncSummary(
                    syncedPlaylistCount = remotePlaylists.size,
                    syncedFavoriteCount = syncedNewFavoriteCount
                )
            )
        } catch (e: SyncApiException) {
            Result.Error(e.error)
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    override suspend fun logout() {
        accountStore.clearSession()
    }

    private suspend fun authenticateCellphone(
        phone: String,
        password: String?,
        captcha: String?
    ): Result<NeteaseAccountSession> {
        val baseUrl = preferences.neteaseCloudApiBaseUrl.first()
        if (baseUrl.isBlank()) return Result.Error(AppError.Api(message = "请先设置网易云增强版接口地址"))
        return try {
            val response = enhancedApi.loginCellphone(
                url = buildEndpoint(baseUrl, "login/cellphone"),
                phone = phone,
                password = password?.takeIf { it.isNotBlank() },
                captcha = captcha?.takeIf { it.isNotBlank() },
                realIp = DEFAULT_REAL_IP,
                timestamp = now()
            )
            if (response.codeOrDefault() != 200) {
                return Result.Error(
                    AppError.Api(
                        message = response.messageOrDefault("网易云登录失败"),
                        code = response.codeOrDefault()
                    )
                )
            }
            val session = response.toAccountSession()
                ?: return Result.Error(AppError.Parse(message = "登录成功，但账号信息解析失败"))
            accountStore.saveSession(session)
            Result.Success(session)
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    private suspend fun syncRemotePlaylist(
        baseUrl: String,
        session: NeteaseAccountSession,
        remotePlaylist: RemotePlaylistSummary,
        remoteOrder: Int
    ) {
        val ownerUid = session.userId.toString()
        val mapping = playlistRemoteMapDao.getByRemoteSource(
            sourcePlatform = Platform.NETEASE.id,
            sourcePlaylistId = remotePlaylist.id,
            ownerUid = ownerUid
        )
        val syncedAt = now()
        val remoteSignature = remotePlaylist.toRemoteSignature()
        if (mapping != null && mapping.remoteSignature == remoteSignature) {
            if (mapping.remoteOrder != remoteOrder) {
                playlistRemoteMapDao.insert(
                    mapping.copy(
                        remoteOrder = remoteOrder,
                        lastSyncedAt = syncedAt
                    )
                )
            }
            return
        }

        val tracksFromPublicApi = when (val detailResult = onlineRepo.getPlaylistDetail(Platform.NETEASE, remotePlaylist.id)) {
            is Result.Success -> detailResult.data
            is Result.Error -> throw SyncApiException(detailResult.error)
            is Result.Loading -> return
        }
        val tracks = resolvePlaylistTracksForSync(
            baseUrl = baseUrl,
            session = session,
            remotePlaylist = remotePlaylist,
            tracksFromPublicApi = tracksFromPublicApi
        )

        if (mapping == null) {
            val remoteSongSignature = tracks.toTrackSignature()
            val newPlaylistId = UUID.randomUUID().toString()
            upsertPlaylistAndSongs(
                playlistId = newPlaylistId,
                playlistName = remotePlaylist.name,
                coverUrl = remotePlaylist.coverUrl,
                tracks = tracks,
                syncedAt = syncedAt
            )
            playlistRemoteMapDao.insert(
                PlaylistRemoteMapEntity(
                    playlistId = newPlaylistId,
                    sourcePlatform = Platform.NETEASE.id,
                    sourcePlaylistId = remotePlaylist.id,
                    ownerUid = ownerUid,
                    remoteSignature = remoteSignature,
                    lastSyncedSongSignature = remoteSongSignature,
                    remoteOrder = remoteOrder,
                    lastSyncedAt = syncedAt
                )
            )
            return
        }

        val localSongs = playlistSongsDao.getSongsByPlaylistOnce(mapping.playlistId)
        val mergedTracks = mergeRemoteAndLocalTracks(
            remoteTracks = tracks,
            localSongs = localSongs
        )
        val mergedSongSignature = mergedTracks.toTrackSignature()

        upsertPlaylistAndSongs(
            playlistId = mapping.playlistId,
            playlistName = remotePlaylist.name,
            coverUrl = remotePlaylist.coverUrl,
            tracks = mergedTracks,
            syncedAt = syncedAt
        )
        playlistRemoteMapDao.insert(
            PlaylistRemoteMapEntity(
                playlistId = mapping.playlistId,
                sourcePlatform = Platform.NETEASE.id,
                sourcePlaylistId = remotePlaylist.id,
                ownerUid = ownerUid,
                remoteSignature = remoteSignature,
                lastSyncedSongSignature = mergedSongSignature,
                remoteOrder = remoteOrder,
                lastSyncedAt = syncedAt
            )
        )
    }

    private suspend fun syncRemotePlaylistWithRetry(
        baseUrl: String,
        session: NeteaseAccountSession,
        remotePlaylist: RemotePlaylistSummary,
        remoteOrder: Int
    ) {
        var attempt = 0
        while (true) {
            try {
                syncRemotePlaylist(
                    baseUrl = baseUrl,
                    session = session,
                    remotePlaylist = remotePlaylist,
                    remoteOrder = remoteOrder
                )
                return
            } catch (e: SyncApiException) {
                if (!e.error.isRateLimitError() || attempt >= NETEASE_RATE_LIMIT_RETRY_COUNT) {
                    throw e
                }
            }
            delay(calculateRateLimitBackoff(attempt))
            attempt += 1
        }
    }

    private suspend fun upsertPlaylistAndSongs(
        playlistId: String,
        playlistName: String,
        coverUrl: String,
        tracks: List<Track>,
        syncedAt: Long
    ) {
        val currentEntity = playlistsDao.getById(playlistId)
        playlistsDao.insert(
            PlaylistEntity(
                playlistId = playlistId,
                name = playlistName,
                coverUrl = coverUrl,
                createdAt = currentEntity?.createdAt ?: syncedAt,
                updatedAt = syncedAt
            )
        )
        playlistSongsDao.replacePlaylistSongs(
            playlistId = playlistId,
            entities = tracks.mapIndexed { index, track ->
                track.toPlaylistSongEntity(playlistId, index)
            }
        )
    }

    private suspend fun resolvePlaylistTracksForSync(
        baseUrl: String,
        session: NeteaseAccountSession,
        remotePlaylist: RemotePlaylistSummary,
        tracksFromPublicApi: List<Track>
    ): List<Track> {
        val expectedTrackCount = remotePlaylist.trackCount ?: return tracksFromPublicApi
        if (expectedTrackCount <= 0 || tracksFromPublicApi.size >= expectedTrackCount) {
            return tracksFromPublicApi
        }

        val tracksFromEnhancedApi = try {
            fetchPlaylistTracksFromEnhancedApi(
                baseUrl = baseUrl,
                session = session,
                playlistId = remotePlaylist.id,
                expectedTrackCount = expectedTrackCount
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

        return if (tracksFromEnhancedApi.size > tracksFromPublicApi.size) {
            tracksFromEnhancedApi
        } else {
            tracksFromPublicApi
        }
    }

    private suspend fun fetchPlaylistTracksFromEnhancedApi(
        baseUrl: String,
        session: NeteaseAccountSession,
        playlistId: String,
        expectedTrackCount: Int
    ): List<Track> {
        val tracks = mutableListOf<Track>()
        var offset = 0
        var resolvedTotalCount = expectedTrackCount

        while (true) {
            val response = requestWithRateLimitRetry {
                enhancedApi.playlistTrackAll(
                    url = buildEndpoint(baseUrl, "playlist/track/all"),
                    id = playlistId,
                    limit = NETEASE_PLAYLIST_TRACK_PAGE_SIZE,
                    offset = offset,
                    cookie = session.cookie,
                    realIp = DEFAULT_REAL_IP,
                    timestamp = now()
                )
            }
            if (response.codeOrDefault() != 200) {
                throw SyncApiException(
                    AppError.Api(
                        message = response.messageOrDefault("获取网易云歌单歌曲失败"),
                        code = response.codeOrDefault()
                    )
                )
            }

            val pageTracks = extractTracksFromPlaylistTrackAllResponse(response)
            if (pageTracks.isEmpty()) break
            tracks += pageTracks

            response.playlistTrackTotalCount()?.let { totalCount ->
                if (totalCount > resolvedTotalCount) {
                    resolvedTotalCount = totalCount
                }
            }
            offset += pageTracks.size
            if (tracks.size >= resolvedTotalCount || pageTracks.size < NETEASE_PLAYLIST_TRACK_PAGE_SIZE) {
                break
            }
            delay(NETEASE_SONG_DETAIL_SYNC_INTERVAL_MS)
        }

        return tracks
    }

    private suspend fun alignNeteaseFavoritesOrder(likedSongIds: List<String>) {
        if (likedSongIds.isEmpty()) return
        val baseAddedAt = now() + likedSongIds.size.toLong()
        likedSongIds.forEachIndexed { index, songId ->
            favoritesDao.updateAddedAt(
                songId = songId,
                platform = Platform.NETEASE.id,
                addedAt = baseAddedAt - index
            )
        }
    }

    private suspend fun resolveNeteaseFavoritesOrder(
        ownerUid: String,
        likedPlaylistRemoteId: String?,
        likedSongIds: List<String>
    ): List<String> {
        if (likedSongIds.isEmpty()) return emptyList()
        if (likedPlaylistRemoteId.isNullOrBlank()) return likedSongIds

        val mapping = playlistRemoteMapDao.getByRemoteSource(
            sourcePlatform = Platform.NETEASE.id,
            sourcePlaylistId = likedPlaylistRemoteId,
            ownerUid = ownerUid
        ) ?: return likedSongIds

        val orderedSongIds = playlistSongsDao.getSongsByPlaylistOnce(mapping.playlistId)
            .asSequence()
            .filter { it.platform == Platform.NETEASE.id && it.songId.isNotBlank() }
            .map { it.songId }
            .distinct()
            .toList()

        if (orderedSongIds.isEmpty()) return likedSongIds

        val orderedSongIdSet = orderedSongIds.toHashSet()
        val appendSongIds = likedSongIds.filterNot(orderedSongIdSet::contains)
        return (orderedSongIds + appendSongIds).distinct()
    }

    private suspend fun fetchNeteaseTracksByIds(songIds: List<String>): List<Track> {
        if (songIds.isEmpty()) return emptyList()
        val tracks = mutableListOf<Track>()
        val chunks = songIds.chunked(NETEASE_BATCH_SIZE)
        chunks.forEachIndexed { index, chunk ->
            val response = requestWithRateLimitRetry {
                tuneHubApi.getNeteaseSongDetail(
                    ids = chunk.joinToString(prefix = "[", postfix = "]")
                )
            }
            tracks += extractNeteaseSongTracks(response)
            if (index < chunks.lastIndex) {
                delay(NETEASE_SONG_DETAIL_SYNC_INTERVAL_MS)
            }
        }
        return tracks
    }

    private suspend fun requestWithRateLimitRetry(
        request: suspend () -> JsonElement
    ): JsonElement {
        var attempt = 0
        while (true) {
            val response = request()
            if (!response.isRateLimitResponse() || attempt >= NETEASE_RATE_LIMIT_RETRY_COUNT) {
                return response
            }
            delay(calculateRateLimitBackoff(attempt))
            attempt += 1
        }
    }

    private fun buildEndpoint(baseUrl: String, path: String): String {
        val trimmedBaseUrl = baseUrl.trim().trimEnd('/')
        val normalizedPath = path.trimStart('/')
        return if (trimmedBaseUrl.endsWith(normalizedPath, ignoreCase = true)) {
            trimmedBaseUrl
        } else {
            "$trimmedBaseUrl/$normalizedPath"
        }
    }

    private fun JsonElement.toAccountSession(cookieOverride: String? = null): NeteaseAccountSession? {
        val root = jsonObjectOrNull() ?: return null
        val data = root.jsonObjectAt("data")
        val profile = root.jsonObjectAt("profile") ?: data?.jsonObjectAt("profile")
        val account = root.jsonObjectAt("account") ?: data?.jsonObjectAt("account")
        val cookie = cookieOverride ?: root.string("cookie") ?: data?.string("cookie") ?: return null
        val userId = profile?.long("userId")
            ?: account?.long("id")
            ?: return null
        val nickname = profile?.string("nickname").orEmpty().ifBlank { "网易云用户$userId" }
        val avatarUrl = profile?.string("avatarUrl").orEmpty()
        return NeteaseAccountSession(
            userId = userId,
            nickname = nickname,
            avatarUrl = avatarUrl,
            cookie = cookie,
            lastLoginAt = now()
        )
    }

    private fun JsonObject.toRemotePlaylist(): RemotePlaylistSummary? {
        val id = long("id")?.toString() ?: string("id") ?: return null
        val name = string("name").orEmpty().ifBlank { return null }
        val coverUrl = string("coverImgUrl").orEmpty()
        val trackCount = int("trackCount")
        val updateTime = long("updateTime")
        val specialType = int("specialType")
        return RemotePlaylistSummary(
            id = id,
            name = name,
            coverUrl = coverUrl,
            trackCount = trackCount,
            updateTime = updateTime,
            specialType = specialType
        )
    }

    private fun JsonElement.codeOrDefault(): Int = jsonObjectOrNull()?.int("code") ?: -1

    private fun JsonElement.messageOrDefault(default: String): String =
        jsonObjectOrNull()?.string("message")
            ?: jsonObjectOrNull()?.string("msg")
            ?: default

    private fun JsonElement.isRateLimitResponse(): Boolean {
        val code = codeOrDefault()
        if (code in NETEASE_RATE_LIMIT_CODES) return true
        return messageOrDefault("").containsRateLimitKeyword()
    }

    private fun JsonElement.shouldClearSavedSession(): Boolean {
        val code = codeOrDefault()
        if (code == 301) return true
        val message = messageOrDefault("").lowercase()
        if (message.isBlank()) return false
        return listOf("未登录", "需要登录", "重新登录", "登录失效", "登录过期").any { keyword ->
            keyword in message
        }
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonObject.jsonObjectAt(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

    private fun JsonObject.jsonArrayAt(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? = string(key)?.toLongOrNull()

    private fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()

    private fun JsonElement.playlistTrackTotalCount(): Int? {
        val root = jsonObjectOrNull() ?: return null
        return root.int("total")
            ?: root.int("count")
            ?: root.jsonObjectAt("playlist")?.int("trackCount")
            ?: root.jsonObjectAt("data")?.int("total")
            ?: root.jsonObjectAt("data")?.int("count")
    }

    private fun extractTracksFromPlaylistTrackAllResponse(response: JsonElement): List<Track> {
        val directTracks = extractNeteaseSongTracks(response)
        if (directTracks.isNotEmpty()) return directTracks

        val root = response.jsonObjectOrNull() ?: return emptyList()
        val data = root.jsonObjectAt("data") ?: return emptyList()
        return extractNeteaseSongTracks(data)
    }

    private fun AppError.isRateLimitError(): Boolean {
        return when (this) {
            is AppError.Api -> code in NETEASE_RATE_LIMIT_CODES || message.containsRateLimitKeyword()
            is AppError.Network -> message.containsRateLimitKeyword() ||
                cause?.message.orEmpty().containsRateLimitKeyword()
            else -> message.containsRateLimitKeyword()
        }
    }

    private fun String.containsRateLimitKeyword(): Boolean {
        if (isBlank()) return false
        val normalized = lowercase()
        return NETEASE_RATE_LIMIT_KEYWORDS.any { keyword -> keyword in normalized }
    }

    private fun calculateRateLimitBackoff(attempt: Int): Long {
        return NETEASE_RATE_LIMIT_BASE_DELAY_MS +
            attempt * NETEASE_RATE_LIMIT_INCREMENT_DELAY_MS
    }

    private fun RemotePlaylistSummary.toRemoteSignature(): String {
        return if (updateTime != null || trackCount != null) {
            "ut=${updateTime ?: -1}|tc=${trackCount ?: -1}|n=$name|c=$coverUrl"
        } else {
            "n=$name|c=$coverUrl|tc=${trackCount ?: -1}"
        }
    }

    private fun RemotePlaylistSummary.isLikedSongsPlaylist(): Boolean {
        if (specialType == NETEASE_LIKED_PLAYLIST_SPECIAL_TYPE) return true
        val normalizedName = name.trim()
        return normalizedName == "我喜欢的音乐" || normalizedName == "喜欢的音乐"
    }

    private fun List<Track>.toTrackSignature(): String =
        joinToString(separator = "|") { "${it.platform.id}:${it.id}" }

    private fun mergeRemoteAndLocalTracks(
        remoteTracks: List<Track>,
        localSongs: List<PlaylistSongEntity>
    ): List<Track> {
        if (localSongs.isEmpty()) return remoteTracks
        if (remoteTracks.isEmpty()) return localSongs.map { it.toTrack() }

        val remoteTrackByKey = LinkedHashMap<String, Track>()
        remoteTracks.forEach { track ->
            remoteTrackByKey.putIfAbsent(track.trackMergeKey(), track)
        }
        val orderedKeys = remoteTrackByKey.keys.toMutableList()

        val localTracks = localSongs.map { it.toTrack() }
        val localTrackByKey = localTracks.associateBy { it.trackMergeKey() }
        val localKeys = localTracks.map { it.trackMergeKey() }
        val localOnlyKeys = localKeys.filterNot(remoteTrackByKey::containsKey)
        if (localOnlyKeys.isEmpty()) {
            return orderedKeys.mapNotNull(remoteTrackByKey::get)
        }

        localOnlyKeys.forEach { localOnlyKey ->
            if (localOnlyKey in orderedKeys) return@forEach

            val localIndex = localKeys.indexOf(localOnlyKey)
            if (localIndex < 0) return@forEach

            val previousAnchorKey = (localIndex - 1 downTo 0)
                .firstNotNullOfOrNull { index ->
                    localKeys[index].takeIf { it in orderedKeys }
                }
            val nextAnchorKey = ((localIndex + 1) until localKeys.size)
                .firstNotNullOfOrNull { index ->
                    localKeys[index].takeIf { it in orderedKeys }
                }

            val insertIndex = when {
                previousAnchorKey != null && nextAnchorKey != null -> {
                    val previousIndex = orderedKeys.indexOf(previousAnchorKey)
                    val nextIndex = orderedKeys.indexOf(nextAnchorKey)
                    if (previousIndex in 0 until nextIndex) nextIndex else previousIndex + 1
                }
                previousAnchorKey != null -> orderedKeys.indexOf(previousAnchorKey) + 1
                nextAnchorKey != null -> orderedKeys.indexOf(nextAnchorKey)
                else -> orderedKeys.size
            }.coerceIn(0, orderedKeys.size)

            orderedKeys.add(insertIndex, localOnlyKey)
        }

        return orderedKeys.mapNotNull { key ->
            remoteTrackByKey[key] ?: localTrackByKey[key]
        }
    }

    private fun Track.trackMergeKey(): String = "${platform.id}:${id}"

    private suspend fun recoverSessionFromPlaylistProbe(
        baseUrl: String,
        savedSession: NeteaseAccountSession,
        originalError: AppError
    ): Result<NeteaseAccountSession?> {
        return try {
            val response = enhancedApi.userPlaylist(
                url = buildEndpoint(baseUrl, "user/playlist"),
                uid = savedSession.userId,
                limit = 1,
                offset = 0,
                cookie = savedSession.cookie,
                realIp = DEFAULT_REAL_IP,
                timestamp = now()
            )
            when {
                response.codeOrDefault() == 200 -> {
                    accountStore.saveSession(savedSession)
                    Result.Success(savedSession)
                }
                response.shouldClearSavedSession() -> {
                    accountStore.clearSession()
                    Result.Success(null)
                }
                else -> Result.Error(originalError)
            }
        } catch (_: Exception) {
            Result.Error(originalError)
        }
    }

    private fun now(): Long = System.currentTimeMillis()

    private class SyncApiException(
        val error: AppError
    ) : IllegalStateException(error.message, error.cause)

    private data class RemotePlaylistSummary(
        val id: String,
        val name: String,
        val coverUrl: String,
        val trackCount: Int? = null,
        val updateTime: Long? = null,
        val specialType: Int? = null
    )

    private companion object {
        const val DEFAULT_REAL_IP = "116.25.146.177"
        const val NETEASE_LIKED_PLAYLIST_SPECIAL_TYPE = 5
        const val NETEASE_BATCH_SIZE = 200
        const val NETEASE_RATE_LIMIT_RETRY_COUNT = 2
        const val NETEASE_RATE_LIMIT_BASE_DELAY_MS = 700L
        const val NETEASE_RATE_LIMIT_INCREMENT_DELAY_MS = 500L
        const val NETEASE_PLAYLIST_SYNC_INTERVAL_MS = 220L
        const val NETEASE_PLAYLIST_TRACK_PAGE_SIZE = 200
        const val NETEASE_SONG_DETAIL_SYNC_INTERVAL_MS = 120L

        val NETEASE_RATE_LIMIT_CODES = setOf(405, 406, 429, 509, -460, -461, -462)
        val NETEASE_RATE_LIMIT_KEYWORDS = listOf(
            "操作频繁",
            "请求频繁",
            "过于频繁",
            "too frequent",
            "rate limit",
            "频率限制"
        )
    }
}
