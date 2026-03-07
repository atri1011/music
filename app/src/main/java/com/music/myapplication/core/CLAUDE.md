[根目录](../../../../CLAUDE.md) > [app](../../../) > [core](../) > **core**

# Core 模块

## 模块职责

提供应用基础设施层，包括通用工具类、数据库抽象、DataStore 偏好设置、网络层（Retrofit API + Dispatch 引擎）。

## 子模块概览

### core/common
- `Result.kt` -- `sealed interface Result<T>` (Success / Error / Loading)，全局统一结果包装，附带 `map`、`onSuccess`、`onError`、`getOrNull` 扩展
- `AppError.kt` -- `sealed class AppError`，分类: Network / Api / Template / Parse / Playback / Database / Unknown
- `DispatchersProvider.kt` -- 可注入的协程调度器提供者，方便单元测试替换
- `CoverImageCacheKey.kt` -- 封面图片缓存 key 工具

### core/database
- **AppDatabase.kt** -- Room 数据库定义，version 2，包含 5 张表
- **entity/**: `FavoriteEntity`, `RecentPlayEntity`, `PlaylistEntity`, `PlaylistSongEntity`, `LyricsCacheEntity`
- **dao/**: `FavoritesDao`, `RecentPlaysDao`, `PlaylistsDao`, `PlaylistSongsDao`, `LyricsCacheDao`
- **mapper/EntityMappers.kt** -- Entity <-> Domain Model 双向转换扩展函数

#### 数据库 Schema

| 表名 | 主键 | 说明 |
|------|------|------|
| `favorites` | (song_id, platform) 复合主键 | 收藏歌曲 |
| `recent_plays` | id (自增), unique(song_id, platform) | 最近播放 + 播放次数统计 |
| `playlists` | playlistId | 用户自建歌单 |
| `playlist_songs` | (playlistId, songId, platform) | 歌单内歌曲 |
| `lyrics_cache` | cacheKey ("platform:songId") | 歌词缓存，含翻译 |

### core/datastore
- `PlayerPreferences.kt` -- DataStore 存储播放模式、音质、平台偏好、API Key
- `HomeContentCacheStore.kt` -- DataStore 按日缓存首页榜单/歌单数据，避免重复请求

### core/network
- **retrofit/TuneHubApi.kt** -- Retrofit 接口，定义 `parse`（解析播放 URL/歌词）、`getMethodTemplate`（方法下发）、平台直连 API（网易/QQ/酷我）
- **dispatch/**:
  - `DispatchExecutor.kt` -- 核心引擎: 获取模板 -> 渲染 -> 请求 -> 转换
  - `TemplateRenderer.kt` -- 模板变量替换（支持 `{{var}}`、`||` fallback、`parseInt()` 等函数）
  - `TransformEngine.kt` -- JSON 响应 -> `List<Track>` 转换（规则化 + 智能回退）
  - `DispatchTemplateCache.kt` -- 内存模板缓存
  - `DispatchModels.kt` -- Dispatch 数据模型
  - `RequestValidator.kt` -- URL 验证
- **interceptor/**:
  - `ApiKeyInterceptor.kt` -- 自动注入 API Key
  - `UserAgentInterceptor.kt` -- User-Agent 设置

## 关键依赖与配置

- Room: `fallbackToDestructiveMigration(dropAllTables = true)` -- 版本升级破坏性迁移
- OkHttp: 15s 超时，DEBUG 模式 BODY 级日志
- Retrofit: kotlinx-serialization JSON converter
- DataStore: 两个独立 store (`player_preferences`, `home_content_cache`)

## 测试与质量

| 测试文件 | 目标 |
|---------|------|
| `TemplateRendererTest.kt` | 模板渲染: 变量替换、函数调用、fallback |
| `TransformEngineTest.kt` | JSON 转换: 规则化解析 + 智能回退 |

缺口: DispatchExecutor、RequestValidator、DAO 层均无测试。

## 相关文件清单

```
app/src/main/java/com/music/myapplication/core/
  common/
    Result.kt, AppError.kt, DispatchersProvider.kt, CoverImageCacheKey.kt
  database/
    AppDatabase.kt
    dao/  FavoritesDao.kt, RecentPlaysDao.kt, PlaylistsDao.kt, PlaylistSongsDao.kt, LyricsCacheDao.kt
    entity/  FavoriteEntity.kt, RecentPlayEntity.kt, PlaylistEntity.kt, PlaylistSongEntity.kt, LyricsCacheEntity.kt
    mapper/  EntityMappers.kt
  datastore/
    PlayerPreferences.kt, HomeContentCacheStore.kt
  network/
    retrofit/  TuneHubApi.kt
    dispatch/  DispatchExecutor.kt, TemplateRenderer.kt, TransformEngine.kt, DispatchTemplateCache.kt, DispatchModels.kt, RequestValidator.kt
    interceptor/  ApiKeyInterceptor.kt, UserAgentInterceptor.kt
```

## 变更记录 (Changelog)

| 日期 | 操作 | 说明 |
|------|------|------|
| 2026-03-08 | 初始化 | 初次生成文档 |
