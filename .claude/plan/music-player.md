# Android 音乐播放器 - 实施计划

## 📋 概要

开发一款仿 QQ 音乐风格的 Android 音乐播放器，通过 TuneHub V3 API 获取在线音乐，支持收藏、最近播放、自建歌单等本地功能。

## 任务类型

- [x] 前端 (→ Gemini)
- [x] 后端 (→ Codex)
- [x] 全栈 (→ 并行)

---

## 技术方案

### 架构决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 架构模式 | 单模块分层 MVVM + Repository | 快速落地，按包边界预留多模块拆分空间 |
| DI 框架 | Hilt | 编译期校验，Android 生态最佳实践 |
| 网络层 | Retrofit（固定接口）+ OkHttp（动态 dispatch） | 混合策略，静态接口强类型 + 动态请求灵活 |
| 本地数据库 | Room + DataStore（偏好设置） | Room 处理结构化数据，DataStore 存设置项 |
| 媒体播放 | Media3 + MediaSessionService | 支持后台播放、通知栏控制、音频焦点 |
| 图片加载 | Coil | Kotlin-first，Compose 原生支持 |
| 导航 | Navigation Compose + 类型安全路由 | 官方推荐，与 Compose 深度集成 |
| 播放器 UI | BottomSheetScaffold（非独立导航目的地） | QQ 音乐风格，mini/full 无缝切换 |
| 主题 | 自定义 M3 ColorScheme（#31C27C 品牌绿） | 保持品牌一致性，不使用系统动态色 |
| minSdk | 26（从 36 降低） | 覆盖 Android 8.0+，市场覆盖率 >95% |

### API 架构

- **Base URL**: `https://tunehub.sayqz.com/api`
- **核心解析**: `POST /v1/parse`（需 `X-API-Key` header，消耗积分）
- **方法下发**: `GET /v1/methods/:platform/:function`（免积分，客户端自行请求上游平台）
  - `search`：搜索歌曲（模板变量：`{{keyword}}`, `{{page}}`, `{{pageSize}}`）
  - `toplists`：排行榜列表
  - `toplist`：排行榜详情（模板变量：`{{id}}`）
  - `playlist`：歌单详情（模板变量：`{{id}}`）
- **支持平台**: netease（网易云）、qq（QQ 音乐）、kuwo（酷我）
- **音质**: 128k / 320k / flac / flac24bit

---

## 包结构

```text
com.music.myapplication
├─ app/
│  ├─ MusicApplication.kt          # Application + Hilt 入口
│  ├─ MainActivity.kt              # 单 Activity
│  ├─ AppRoot.kt                   # BottomSheetScaffold 根布局
│  └─ navigation/
│     ├─ AppNavGraph.kt            # NavHost 定义
│     └─ Routes.kt                 # 路由定义（sealed class）
├─ core/
│  ├─ common/
│  │  ├─ Result.kt                 # 统一结果封装
│  │  ├─ AppError.kt               # 统一错误模型
│  │  └─ DispatchersProvider.kt    # 协程调度器注入
│  ├─ network/
│  │  ├─ retrofit/
│  │  │  ├─ TuneHubApi.kt          # POST /v1/parse 接口定义
│  │  │  └─ NetworkModule.kt       # Hilt 网络模块
│  │  ├─ dispatch/
│  │  │  ├─ DispatchExecutor.kt    # 方法下发执行器
│  │  │  ├─ DispatchTemplateCache.kt # 模板缓存（TTL）
│  │  │  ├─ TemplateRenderer.kt    # 模板变量替换
│  │  │  ├─ RequestValidator.kt    # URL/Host 白名单校验
│  │  │  ├─ TransformEngine.kt     # 响应转换（预定义映射，不执行脚本）
│  │  │  └─ DispatchModels.kt      # 方法下发相关模型
│  │  └─ interceptor/
│  │     ├─ ApiKeyInterceptor.kt   # API Key 统一注入
│  │     └─ UserAgentInterceptor.kt
│  ├─ database/
│  │  ├─ AppDatabase.kt            # Room 数据库
│  │  ├─ dao/
│  │  │  ├─ FavoritesDao.kt
│  │  │  ├─ RecentPlaysDao.kt
│  │  │  ├─ PlaylistsDao.kt
│  │  │  ├─ PlaylistSongsDao.kt
│  │  │  └─ LyricsCacheDao.kt
│  │  ├─ entity/                   # Room 实体
│  │  └─ mapper/                   # Entity ↔ Domain 映射
│  └─ datastore/
│     └─ PlayerPreferences.kt      # 播放模式、音质偏好
├─ domain/
│  ├─ model/
│  │  ├─ Track.kt                  # 核心歌曲模型
│  │  ├─ Playlist.kt               # 歌单模型
│  │  ├─ LyricLine.kt             # 歌词行模型
│  │  ├─ PlaybackState.kt         # 播放状态
│  │  ├─ PlaybackMode.kt          # SEQUENTIAL/SHUFFLE/REPEAT_ONE
│  │  └─ Platform.kt              # NETEASE/QQ/KUWO 枚举
│  ├─ repository/                  # 仓库接口定义
│  │  ├─ OnlineMusicRepository.kt
│  │  ├─ LocalLibraryRepository.kt
│  │  └─ PlaybackRepository.kt
│  └─ usecase/                     # 用例层
│     ├─ SearchSongsUseCase.kt
│     ├─ GetToplistsUseCase.kt
│     ├─ GetPlaylistDetailUseCase.kt
│     ├─ ResolvePlayableUrlUseCase.kt
│     ├─ ToggleFavoriteUseCase.kt
│     ├─ RecordRecentPlayUseCase.kt
│     └─ ManageCustomPlaylistUseCase.kt
├─ data/
│  ├─ remote/
│  │  ├─ dto/                      # 网络传输对象
│  │  │  ├─ ParseRequestDto.kt
│  │  │  ├─ ParseResponseDto.kt
│  │  │  ├─ MethodsTemplateDto.kt
│  │  │  └─ RemoteSongDto.kt
│  │  └─ datasource/
│  │     ├─ TuneHubRemoteDataSource.kt
│  │     └─ DispatchRemoteDataSource.kt
│  ├─ local/datasource/
│  │  └─ LibraryLocalDataSource.kt
│  ├─ repository/                  # 仓库实现
│  │  ├─ OnlineMusicRepositoryImpl.kt
│  │  ├─ LocalLibraryRepositoryImpl.kt
│  │  └─ PlaybackRepositoryImpl.kt
│  └─ mapper/
│     ├─ RemoteToDomainMapper.kt
│     └─ DomainToEntityMapper.kt
├─ feature/
│  ├─ home/
│  │  ├─ HomeScreen.kt
│  │  └─ HomeViewModel.kt
│  ├─ search/
│  │  ├─ SearchScreen.kt
│  │  └─ SearchViewModel.kt
│  ├─ library/
│  │  ├─ LibraryScreen.kt         # "我的" 页面
│  │  └─ LibraryViewModel.kt
│  ├─ playlist/
│  │  ├─ PlaylistDetailScreen.kt
│  │  └─ PlaylistDetailViewModel.kt
│  ├─ player/
│  │  ├─ MiniPlayerBar.kt         # 底部迷你播放条
│  │  ├─ FullScreenPlayer.kt      # 全屏播放页
│  │  ├─ PlayerBottomSheet.kt     # BottomSheet 容器
│  │  ├─ PlayerViewModel.kt       # 全局播放器 VM（Activity scope）
│  │  ├─ LyricsView.kt            # 歌词同步滚动
│  │  └─ RotatingCover.kt         # 旋转封面动画
│  └─ components/                  # 通用 UI 组件
│     ├─ MediaListItem.kt         # 歌曲列表项
│     ├─ PlaylistCard.kt          # 歌单卡片
│     ├─ PlatformFilterChips.kt   # 平台选择筛选
│     ├─ QualitySelector.kt       # 音质选择器
│     ├─ LoadingView.kt
│     └─ ErrorView.kt
├─ media/
│  ├─ service/
│  │  └─ MusicPlaybackService.kt  # MediaSessionService
│  ├─ session/
│  │  ├─ MediaSessionManager.kt
│  │  └─ MediaControllerConnector.kt
│  ├─ player/
│  │  ├─ ExoPlayerFactory.kt
│  │  ├─ QueueManager.kt          # 播放队列管理
│  │  └─ PlaybackModeManager.kt   # 播放模式切换
│  ├─ notification/
│  │  └─ PlaybackNotificationManager.kt
│  └─ state/
│     └─ PlaybackStateStore.kt    # StateFlow 全局播放状态
└─ di/
   ├─ NetworkModule.kt
   ├─ DatabaseModule.kt
   ├─ RepositoryModule.kt
   └─ MediaModule.kt
```

---

## Room 数据库 Schema

### 表结构

| 表名 | 主键 | 关键字段 | 索引 |
|------|------|----------|------|
| `favorites` | `song_id + platform`（组合） | title, artist, album, cover_url, duration_ms, added_at | idx_favorites_added_at |
| `recent_plays` | `id`（自增）| song_id, platform, title, artist, cover_url, played_at, position_ms | idx_recent_played_at（唯一约束：song_id+platform） |
| `playlists` | `playlist_id`（UUID） | name, cover_url, created_at, updated_at | idx_playlists_updated_at |
| `playlist_songs` | `playlist_id + song_id + platform`（组合） | order_in_playlist, added_at, title, artist, cover_url, duration_ms | idx_playlist_songs_order（外键 CASCADE） |
| `lyrics_cache` | `cache_key`（platform:songId） | lyric_text, source, updated_at, expires_at | idx_lyrics_expires_at |

---

## UI 架构

### 页面结构

```
MainActivity
└─ AppRoot (BottomSheetScaffold)
   ├─ sheetContent → PlayerBottomSheet
   │  ├─ fraction < 0.5 → MiniPlayerBar（淡出）
   │  └─ fraction > 0.0 → FullScreenPlayer（淡入）
   │     ├─ TopBar（返回、标题、分享）
   │     ├─ 中部：RotatingCover ↔ LyricsView（点击切换 Crossfade）
   │     └─ 底部：QualitySelector + ProgressSlider + PlaybackControls
   ├─ content → Column
   │  ├─ NavHost (Home / Search / MyMusic / PlaylistDetail)
   │  └─ BottomNavigation（首页/搜索/我的）
   └─ sheetPeekHeight = 72.dp + BottomNav高度
```

### 主题定义

```kotlin
// 品牌色
val QQMusicGreen = Color(0xFF31C27C)

// Light Scheme
lightColorScheme(
    primary = QQMusicGreen,
    onPrimary = Color.White,
    secondary = Color(0xFF28A769),
    surface = Color(0xFFF5F5F5),
    background = Color.White,
    // ... 其他 token
)

// 全屏播放器：动态主题
// 从专辑封面提取 Palette dominant color
// 使用 animateColorAsState(tween(500ms)) 平滑过渡
```

### 动画规格

| 动画 | 实现 | 参数 |
|------|------|------|
| 旋转封面 | `rememberInfiniteTransition` | 0f→360f, 10000ms, 暂停时停在当前角度 |
| 歌词滚动 | `LazyColumn` + `animateScrollToItem` | 跟踪播放时间戳，手动拖拽暂停自动滚动 |
| Mini↔Full 过渡 | BottomSheet drag offset → fraction | alpha 插值，MiniPlayer 淡出 / FullPlayer 淡入 |
| 动态主题色 | `animateColorAsState` | tween(500ms)，专辑切换时触发 |
| 歌词焦点 | `graphicsLayer { alpha }` | 上下边缘淡出，中心高亮当前行 |

### 平台选择器

- **搜索页**：搜索栏下方 `LazyRow` + `FilterChip`（QQ 音乐 / 网易云 / 酷我）
- **音质选择**：播放器内 `OutlinedButton` 显示当前音质，点击弹出 `ModalBottomSheet` 列表

---

## DispatchExecutor 核心流程

```
┌──────────┐    ┌──────────────┐    ┌──────────────┐    ┌─────────┐    ┌──────────┐    ┌────────────┐
│ 拉模板    │ →  │ 渲染参数      │ →  │ 安全校验      │ →  │ 执行请求 │ →  │ 转换结果  │ →  │ 标准化输出  │
│ /methods  │    │ {{keyword}}→值│    │ https+白名单  │    │ OkHttp  │    │ 字段映射  │    │ Track/List │
└──────────┘    └──────────────┘    └──────────────┘    └─────────┘    └──────────┘    └────────────┘
```

### 安全控制

- API Key 仅通过 `ApiKeyInterceptor` 注入（仅 /v1/parse 请求）
- 动态 URL 严格 host 白名单（仅允许已知音乐平台域名）
- 统一超时 15s、重试 3 次、指数退避
- `transform` 仅允许预定义字段映射规则，禁止执行任意脚本

---

## 实施步骤

### 阶段 1：工程基线（M0）

1. **调整 build.gradle.kts**
   - `minSdk` 从 36 降为 26
   - 添加依赖：Hilt、Retrofit、OkHttp、Room、Media3、Coil、Navigation Compose、kotlinx.serialization、DataStore
   - 配置 KSP（Room、Hilt 注解处理）

2. **建立 Hilt 骨架**
   - `MusicApplication` 添加 `@HiltAndroidApp`
   - `MainActivity` 添加 `@AndroidEntryPoint`
   - 创建 `NetworkModule`、`DatabaseModule`、`RepositoryModule`、`MediaModule`

3. **建立基础设施**
   - `Result.kt`、`AppError.kt`、`DispatchersProvider.kt`
   - `Platform.kt` 枚举、`PlaybackMode.kt` 枚举

**预期产物**: 项目可编译通过，Hilt 注入可用

### 阶段 2：核心数据层（M1-数据通路）

4. **Domain 模型**
   - `Track`、`Playlist`、`LyricLine`、`PlaybackState`、`PlaybackMode`

5. **Room 数据库**
   - 5 张表的 Entity + DAO + Database
   - Entity ↔ Domain 映射器
   - 基础单元测试

6. **网络层 - TuneHub 固定接口**
   - `TuneHubApi`：`POST /v1/parse`、`GET /v1/methods/{platform}/{function}`
   - `ApiKeyInterceptor`
   - DTO 定义 + 映射器

7. **网络层 - DispatchExecutor**
   - `DispatchTemplateCache`（内存缓存 + TTL）
   - `TemplateRenderer`（模板变量替换）
   - `RequestValidator`（host 白名单校验）
   - `TransformEngine`（预定义字段映射）
   - 集成测试

8. **Repository 层**
   - `OnlineMusicRepositoryImpl`（search / toplists / toplist / playlist / resolveUrl）
   - `LocalLibraryRepositoryImpl`（favorites / recentPlays / playlists / lyrics）
   - `PlaybackRepositoryImpl`（queue / controls / state）

**预期产物**: 数据层全通，可通过 Repository 获取搜索结果、榜单、歌单

### 阶段 3：媒体播放层（M2-播放通路）

9. **Media3 Service**
    - `MusicPlaybackService : MediaSessionService`
    - `ExoPlayerFactory` 创建播放器实例
    - `MediaSession` 暴露给系统

10. **播放控制**
    - `QueueManager`：播放队列维护、切歌逻辑
    - `PlaybackModeManager`：顺序/随机/单曲循环
    - `PlaybackStateStore`：`StateFlow<PlaybackState>` 全局状态

11. **通知与连接**
    - `PlaybackNotificationManager`：前台通知、锁屏控制
    - `MediaControllerConnector`：UI 侧订阅播放状态
    - 音频焦点处理、耳机拔出暂停

**预期产物**: 可播放在线音乐，通知栏控制生效，后台播放正常

### 阶段 4：UI 层 - 基础框架（M3）

12. **主题与导航**
    - `MusicAppTheme`：QQ 音乐绿色品牌主题（Light/Dark）
    - `Routes` 定义 + `AppNavGraph`
    - `BottomNavigation` 组件

13. **AppRoot + BottomSheetScaffold**
    - `PlayerBottomSheet` 容器
    - `MiniPlayerBar`（迷你播放条）
    - Sheet offset → fraction 过渡逻辑

14. **通用组件**
    - `MediaListItem`（歌曲列表项）
    - `PlaylistCard`（歌单卡片）
    - `PlatformFilterChips`（平台筛选）
    - `LoadingView` / `ErrorView`

**预期产物**: App 可启动，底部导航可切换，迷你播放器可拖拽展开

### 阶段 5：UI 层 - 功能页面（M3 续）

15. **搜索页**
    - `SearchScreen` + `SearchViewModel`
    - 搜索栏 + 平台 FilterChips + 结果列表
    - 防抖搜索、分页加载

16. **首页**
    - `HomeScreen` + `HomeViewModel`
    - 推荐歌单、排行榜入口、快速搜索

17. **我的音乐页**
    - `LibraryScreen` + `LibraryViewModel`
    - 收藏列表、最近播放、自建歌单管理（CRUD）

18. **歌单/榜单详情页**
    - `PlaylistDetailScreen` + `PlaylistDetailViewModel`
    - 歌曲列表 + 全部播放

**预期产物**: 所有业务页面可用，搜索→选歌→播放全链路通

### 阶段 6：UI 层 - 播放器体验（M4）

19. **全屏播放器**
    - `FullScreenPlayer` 完整布局
    - `RotatingCover`：旋转封面动画
    - 播放控制栏：进度条 + 播放模式 + 收藏按钮
    - `QualitySelector`：音质选择 ModalBottomSheet

20. **歌词视图**
    - `LyricsView`：LRC 解析 + 同步滚动
    - 当前行高亮 + 上下边缘淡出效果
    - 手动拖拽暂停自动滚动

21. **动态主题**
    - Palette API 从封面提取主色
    - `animateColorAsState` 平滑过渡背景色
    - 确保文字在深色背景上可读（scrim 覆盖）

**预期产物**: 完整的 QQ 音乐风格播放体验

### 阶段 7：稳定性与优化

22. **错误处理完善**
    - 统一错误分级：网络错误、模板错误、解析错误、播放错误
    - UI 可恢复提示（重试按钮）

23. **性能优化**
    - 图片下采样 + Coil 缓存策略
    - `derivedStateOf` 减少 BottomSheet 重组
    - 搜索防抖、列表惰性加载

24. **安全加固**
    - API Key 存储方案（BuildConfig / 加密 SharedPreferences）
    - Host 白名单严格校验
    - 证书锁定（可选）

---

## 关键流程伪代码

### 搜索流程
```kotlin
suspend fun search(platform: Platform, keyword: String, page: Int, pageSize: Int): List<Track> {
    // 1. 获取搜索方法配置
    val template = dispatchExecutor.fetchTemplate(platform, "search")
    // 2. 渲染模板参数
    val request = dispatchExecutor.buildRequest(template, mapOf(
        "keyword" to keyword, "page" to page.toString(), "pageSize" to pageSize.toString()
    ))
    // 3. 执行请求 + 转换
    val tracks = dispatchExecutor.executeAndTransform(request, template.transform)
    // 4. 聚合收藏状态
    return tracks.map { it.copy(isFavorite = localRepo.isFavorite(it.id, it.platform)) }
}
```

### 播放流程
```kotlin
suspend fun playTrack(track: Track, queue: List<Track>, index: Int) {
    // 1. 解析可播放 URL
    val resolved = onlineRepo.resolvePlayableUrls(track.platform, listOf(track.id), quality)
    val playable = track.copy(playableUrl = resolved.first().url)
    // 2. 设置队列并播放
    playbackRepo.setQueue(queue, startIndex = index)
    playbackRepo.replaceCurrent(playable)
    playbackRepo.play()
    // 3. 记录最近播放
    localRepo.recordRecentPlay(playable)
}
```

### DispatchExecutor 流程
```kotlin
suspend fun executeByMethod(platform: Platform, function: String, args: Map<String, String>): Result<List<Track>> {
    val template = templateCache.getOrFetch(platform, function)       // 拉模板（带缓存）
    val rendered = templateRenderer.render(template, args)            // 渲染参数
    requestValidator.validate(rendered)                               // 安全校验
    val response = httpClient.newCall(rendered.toOkHttpRequest()).await() // 执行
    val transformed = transformEngine.apply(template.transform, response.body) // 转换
    return Result.Success(standardizer.toTracks(transformed))         // 标准化
}
```

---

## 里程碑验收标准

| 里程碑 | 验收标准 |
|--------|----------|
| M0-基线 | 项目编译通过，Hilt 注入可用 |
| M1-数据通路 | 搜索、榜单、歌单数据可获取，错误态可恢复 |
| M2-播放通路 | 可播放/暂停/切歌/拖进度，通知栏控制生效，后台播放正常 |
| M3-页面可用 | 全部业务页面可操作，搜索→选歌→播放链路贯通 |
| M4-体验完整 | QQ 音乐风格播放器交互完成，歌词同步、旋转封面、动态主题均可用 |

---

## 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| API Key 泄露（Android 客户端无法真正保密） | 短期：BuildConfig + ProGuard 混淆；长期：自建 BFF 代理 |
| 方法下发动态 URL 安全风险 | Host 白名单 + HTTPS 强制 + Header 过滤 |
| transform 脚本执行风险 | 仅允许预定义字段映射规则，不执行任意 JS |
| minSdk 36 设备覆盖率极低 | 降为 minSdk 26，覆盖 95%+ 设备 |
| 播放器状态跨页面竞态 | MediaSessionService 为单一状态源，UI 通过 StateFlow 订阅 |
| 上游平台接口变动 | DispatchExecutor 模板缓存带 TTL，服务端可动态更新配置 |

---

## 需要的依赖（build.gradle.kts 新增）

```kotlin
// Hilt
implementation("com.google.dagger:hilt-android:2.51.1")
ksp("com.google.dagger:hilt-compiler:2.51.1")
implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

// Retrofit + OkHttp
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// Room
implementation("androidx.room:room-runtime:2.7.0")
implementation("androidx.room:room-ktx:2.7.0")
ksp("androidx.room:room-compiler:2.7.0")

// Media3
implementation("androidx.media3:media3-exoplayer:1.5.1")
implementation("androidx.media3:media3-session:1.5.1")
implementation("androidx.media3:media3-ui:1.5.1")

// Coil
implementation("io.coil-kt:coil-compose:2.7.0")

// Navigation
implementation("androidx.navigation:navigation-compose:2.8.5")

// DataStore
implementation("androidx.datastore:datastore-preferences:1.1.1")

// Kotlinx Serialization
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

// Palette (动态主题色提取)
implementation("androidx.palette:palette-ktx:1.0.0")
```

---

## SESSION_ID（供 /ccg:execute 使用）

- CODEX_SESSION: 019cb6f4-5a64-7031-8e27-b7dcfcbf8bef
- GEMINI_SESSION: 72b0c6ee-6e63-42c4-9120-71130ee2c156
