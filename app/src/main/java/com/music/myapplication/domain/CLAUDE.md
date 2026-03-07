[根目录](../../../../CLAUDE.md) > [app](../../../) > [domain](../) > **domain**

# Domain 模块

## 模块职责

纯 Kotlin 领域层，定义核心业务模型和 Repository 接口。不依赖任何 Android 框架或第三方库（仅 kotlinx-serialization 用于序列化标注）。

## 入口与启动

无启动逻辑，作为被其他层引用的纯定义层。

## 对外接口

### Repository 接口

#### OnlineMusicRepository
```kotlin
interface OnlineMusicRepository {
    suspend fun search(platform, keyword, page, pageSize): Result<List<Track>>
    suspend fun getToplists(platform): Result<List<ToplistInfo>>
    suspend fun getToplistDetailFast(platform, id): Result<List<Track>>
    suspend fun enrichToplistTracks(platform, id, tracks): List<Track>
    suspend fun getToplistDetail(platform, id): Result<List<Track>>
    suspend fun getPlaylistDetail(platform, id): Result<List<Track>>
    suspend fun resolvePlayableUrl(platform, songId, quality): Result<String>
    suspend fun getLyrics(platform, songId): Result<LyricsResult>
}
```

#### LocalLibraryRepository
收藏管理、最近播放记录、歌单 CRUD、歌词缓存、播放统计查询。所有读取操作返回 `Flow`。

#### RecommendationRepository
```kotlin
interface RecommendationRepository {
    suspend fun getDailyRecommendedTracks(limit): List<Track>
    suspend fun getFmTrack(): Track?
    suspend fun getRecommendedPlaylists(): List<ToplistInfo>
    suspend fun getSimilarTracks(track, limit): List<Track>
}
```

### 数据模型

| 模型 | 说明 | 序列化 |
|------|------|--------|
| `Track` | 歌曲核心模型 (id, platform, title, artist, album, coverUrl, durationMs, playableUrl, isFavorite, quality) | @Serializable |
| `Playlist` | 歌单 (id, name, coverUrl, trackCount, tracks, timestamps) | 非序列化 |
| `Platform` | 平台枚举: NETEASE / QQ / KUWO，带 id 和 displayName | @Serializable |
| `PlaybackState` | 播放状态 (currentTrack, isPlaying, position, duration, mode, queue, index, quality) | 非序列化 |
| `PlaybackMode` | 播放模式枚举: SEQUENTIAL / SHUFFLE / REPEAT_ONE | 非序列化 |
| `LyricLine` | 歌词行 (timeMs, text, translation) | 非序列化 |
| `ToplistInfo` | 榜单信息 (id, name, coverUrl, description) | @Serializable |
| `LyricsResult` | 歌词结果 (lyric, translation?) | 非序列化 |

## 关键依赖与配置

- 仅依赖 `kotlinx.serialization` 注解
- 使用 `core.common.Result` 作为返回类型包装

## 测试与质量

领域层无直接测试；通过 Repository 实现层的测试间接覆盖。

## 相关文件清单

```
app/src/main/java/com/music/myapplication/domain/
  model/
    Track.kt, Playlist.kt, Platform.kt, PlaybackState.kt, PlaybackMode.kt, LyricLine.kt
  repository/
    OnlineMusicRepository.kt, LocalLibraryRepository.kt, RecommendationRepository.kt
```

## 变更记录 (Changelog)

| 日期 | 操作 | 说明 |
|------|------|------|
| 2026-03-08 | 初始化 | 初次生成文档 |
