package com.music.myapplication.data.repository

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.database.dao.FavoritesDao
import com.music.myapplication.core.database.dao.PlaylistRemoteMapDao
import com.music.myapplication.core.database.dao.PlaylistSongsDao
import com.music.myapplication.core.database.dao.PlaylistsDao
import com.music.myapplication.core.database.entity.PlaylistEntity
import com.music.myapplication.core.database.entity.PlaylistRemoteMapEntity
import com.music.myapplication.core.database.mapper.toFavoriteEntity
import com.music.myapplication.core.database.mapper.toPlaylistSongEntity
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
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    override suspend fun syncLocalLibrary(): Result<NeteaseSyncSummary> {
        val baseUrl = preferences.neteaseCloudApiBaseUrl.first()
        if (baseUrl.isBlank()) return Result.Error(AppError.Api(message = "请先设置网易云增强版接口地址"))
        val currentSession = accountStore.session.first()
            ?: return Result.Error(AppError.Api(message = "请先登录网易云账号"))
        return try {
            val playlistResponse = enhancedApi.userPlaylist(
                url = buildEndpoint(baseUrl, "user/playlist"),
                uid = currentSession.userId,
                cookie = currentSession.cookie,
                realIp = DEFAULT_REAL_IP,
                timestamp = now()
            )
            if (playlistResponse.codeOrDefault() != 200) {
                return Result.Error(
                    AppError.Api(
                        message = playlistResponse.messageOrDefault("获取网易云歌单失败"),
                        code = playlistResponse.codeOrDefault()
                    )
                )
            }

            val remotePlaylists = playlistResponse.jsonObjectOrNull()?.jsonArrayAt("playlist")
                ?.mapNotNull { it.jsonObjectOrNull()?.toRemotePlaylist() }
                .orEmpty()

            remotePlaylists.forEach { remotePlaylist ->
                syncRemotePlaylist(
                    session = currentSession,
                    remotePlaylist = remotePlaylist
                )
            }

            val likeListResponse = enhancedApi.likeList(
                url = buildEndpoint(baseUrl, "likelist"),
                uid = currentSession.userId,
                cookie = currentSession.cookie,
                realIp = DEFAULT_REAL_IP,
                timestamp = now()
            )
            if (likeListResponse.codeOrDefault() != 200) {
                return Result.Error(
                    AppError.Api(
                        message = likeListResponse.messageOrDefault("获取喜欢音乐失败"),
                        code = likeListResponse.codeOrDefault()
                    )
                )
            }

            val likedSongIds = likeListResponse.jsonObjectOrNull()?.jsonArrayAt("ids")
                ?.mapNotNull { it.jsonPrimitiveOrNull()?.contentOrNull }
                .orEmpty()
            val likedTracks = fetchNeteaseTracksByIds(likedSongIds)
            likedTracks.forEach { favoritesDao.insert(it.copy(isFavorite = true).toFavoriteEntity()) }

            val updatedSession = currentSession.copy(lastSyncAt = now())
            accountStore.saveSession(updatedSession)
            Result.Success(
                NeteaseSyncSummary(
                    syncedPlaylistCount = remotePlaylists.size,
                    syncedFavoriteCount = likedTracks.size
                )
            )
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
        session: NeteaseAccountSession,
        remotePlaylist: RemotePlaylistSummary
    ) {
        val mapping = playlistRemoteMapDao.getByRemoteSource(
            sourcePlatform = Platform.NETEASE.id,
            sourcePlaylistId = remotePlaylist.id,
            ownerUid = session.userId.toString()
        )
        val localPlaylistId = mapping?.playlistId ?: UUID.randomUUID().toString()
        val now = now()
        val currentEntity = playlistsDao.getById(localPlaylistId)

        val playlistEntity = PlaylistEntity(
            playlistId = localPlaylistId,
            name = remotePlaylist.name,
            coverUrl = remotePlaylist.coverUrl,
            createdAt = currentEntity?.createdAt ?: now,
            updatedAt = now
        )
        playlistsDao.insert(playlistEntity)

        when (val detailResult = onlineRepo.getPlaylistDetail(Platform.NETEASE, remotePlaylist.id)) {
            is Result.Success -> {
                val tracks = detailResult.data
                playlistSongsDao.replacePlaylistSongs(
                    playlistId = localPlaylistId,
                    entities = tracks.mapIndexed { index, track ->
                        track.toPlaylistSongEntity(localPlaylistId, index)
                    }
                )
                playlistRemoteMapDao.insert(
                    PlaylistRemoteMapEntity(
                        playlistId = localPlaylistId,
                        sourcePlatform = Platform.NETEASE.id,
                        sourcePlaylistId = remotePlaylist.id,
                        ownerUid = session.userId.toString(),
                        lastSyncedAt = now
                    )
                )
            }
            is Result.Error -> throw IllegalStateException(detailResult.error.message)
            is Result.Loading -> Unit
        }
    }

    private suspend fun fetchNeteaseTracksByIds(songIds: List<String>): List<Track> {
        if (songIds.isEmpty()) return emptyList()
        val tracks = mutableListOf<Track>()
        songIds.chunked(NETEASE_BATCH_SIZE).forEach { chunk ->
            val response = tuneHubApi.getNeteaseSongDetail(
                ids = chunk.joinToString(prefix = "[", postfix = "]")
            )
            tracks += response.jsonObjectOrNull()?.jsonArrayAt("songs")
                ?.mapNotNull { it.jsonObjectOrNull()?.toTrack() }
                .orEmpty()
        }
        return tracks
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
        return RemotePlaylistSummary(id = id, name = name, coverUrl = coverUrl)
    }

    private fun JsonObject.toTrack(): Track? {
        val id = long("id")?.toString() ?: string("id") ?: return null
        val title = string("name").orEmpty().ifBlank { return null }
        val artists = jsonArrayAt("ar")
            ?.mapNotNull { it.jsonObjectOrNull()?.string("name") }
            .orEmpty()
            .joinToString("/")
        val album = jsonObjectAt("al")
        return Track(
            id = id,
            platform = Platform.NETEASE,
            title = title,
            artist = artists.ifBlank { "未知歌手" },
            album = album?.string("name").orEmpty(),
            albumId = album?.long("id")?.toString().orEmpty(),
            coverUrl = album?.string("picUrl").orEmpty(),
            durationMs = long("dt") ?: 0L,
            isFavorite = true
        )
    }

    private fun JsonElement.codeOrDefault(): Int = jsonObjectOrNull()?.int("code") ?: -1

    private fun JsonElement.messageOrDefault(default: String): String =
        jsonObjectOrNull()?.string("message")
            ?: jsonObjectOrNull()?.string("msg")
            ?: default

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

    private data class RemotePlaylistSummary(
        val id: String,
        val name: String,
        val coverUrl: String
    )

    private companion object {
        const val DEFAULT_REAL_IP = "116.25.146.177"
        const val NETEASE_BATCH_SIZE = 200
    }
}
