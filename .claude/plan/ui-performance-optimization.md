# 实施计划：UI 流畅度全面优化

## 任务类型
- [x] 前端 (→ Gemini)
- [x] 后端 (→ Codex)
- [x] 全栈 (→ 并行)

## 背景

用户反馈：滑动、点击等交互有明显卡顿感，不像现代 App。

技术栈：Jetpack Compose + Hilt + Room + Media3 + Coil + Navigation Compose

## 问题分析（按影响排序）

| 优先级 | 问题 | 影响 | 根因 |
|--------|------|------|------|
| P0 | 播放状态订阅粒度过粗 | High | `positionMs` 每 500ms 更新整包 `PlaybackState`，Player 页面所有子组件级联重组 |
| P0 | RotatingCover 组合期副作用 | High | `pausedAngle = angle` 在 composition 中写状态，违反 Compose 规则 |
| P0 | LyricsView 离屏合成开销 | High | `CompositingStrategy.Offscreen + DstIn` 强制 GPU 离屏缓冲 |
| P1 | 图片加载无策略 | Medium-High | `AsyncImage(model=url)` 无 crossfade/placeholder/size/缓存策略控制 |
| P1 | `collectAsState()` 未绑定生命周期 | Medium | 后台/不可见时仍活跃收集 Flow |
| P1 | LazyList 缺 contentType + key | Medium | 列表项复用效率差，Home Grid 无 key |
| P2 | 导航无过渡动画 | Medium（感知） | NavHost 零 transition 配置，页面突变 |
| P2 | MiniPlayerBar clip/shadow 顺序 | Low | `.clip()` 在 `.shadow()` 前导致阴影异常 |

**纠偏**：`MiniPlayerBar` 走的是 `miniPlayerState`（已 `distinctUntilChanged`），不受 500ms position tick 影响。重组风暴主要发生在 **PlayerLyricsScreen 子树**。

## 技术方案

### 综合方案（推荐路径 A → C）

先做低风险高收益改造压制主要 jank，后续上 Macrobenchmark 固化成果。

## 实施步骤

### Step 1 [P0] — 拆分 PlayerViewModel 状态流，隔离 position 重组面

**文件**: `feature/player/PlayerViewModel.kt`

将 `playbackState: StateFlow<PlaybackState>` 拆为两个细粒度流：

```kotlin
// 静态 UI 状态（不含 position/duration，变化频率低）
data class PlayerStaticUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val playbackMode: PlaybackMode = PlaybackMode.SEQUENTIAL,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val quality: String = "128k"
)

// 进度状态（高频，500ms 更新）
data class PlaybackProgressUiState(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

// ViewModel 中：
val staticUiState: StateFlow<PlayerStaticUiState> = stateStore.state
    .map { s -> PlayerStaticUiState(s.currentTrack, s.isPlaying, s.playbackMode, s.queue, s.currentIndex, s.quality) }
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerStaticUiState())

val progressState: StateFlow<PlaybackProgressUiState> = stateStore.state
    .map { s -> PlaybackProgressUiState(s.positionMs, s.durationMs) }
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaybackProgressUiState())
```

### Step 2 [P0] — 重构 PlayerLyricsScreen 订阅，按粒度分离

**文件**: `feature/player/PlayerLyricsScreen.kt`

```kotlin
val staticState by playerViewModel.staticUiState.collectAsStateWithLifecycle()
val progress by playerViewModel.progressState.collectAsStateWithLifecycle()
val currentTrack = staticState.currentTrack

// currentLyricIndex 只依赖 progress.positionMs
val currentLyricIndex by remember(lyricsState.lyrics) {
    derivedStateOf {
        LyricsParser.findCurrentIndex(lyricsState.lyrics, progress.positionMs)
    }
}

// CoverPanel 不接收 progress
CoverPanel(track = currentTrack, isPlaying = staticState.isPlaying)

// PlayerControlsSection 接收拆分后的状态
PlayerControlsSection(
    staticState = staticState,
    progress = progress,
    ...
)
```

### Step 3 [P0] — 重构 PlayerControlsSection 接收拆分状态

**文件**: `feature/player/PlayerControlsSection.kt`

```kotlin
@Composable
fun PlayerControlsSection(
    staticState: PlayerStaticUiState,  // 低频
    progress: PlaybackProgressUiState, // 高频但只影响 Slider + 时间文本
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    onQualityChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Slider + 时间文本读 progress
    // 播放按钮等读 staticState
}
```

### Step 4 [P0] — 修复 RotatingCover 组合期副作用

**文件**: `feature/player/RotatingCover.kt`

```kotlin
@Composable
fun RotatingCover(coverUrl: String, isPlaying: Boolean, modifier: Modifier = Modifier) {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(durationMillis = 10000, easing = LinearEasing)
                )
                rotation.snapTo(rotation.value % 360f)
            }
        }
        // 暂停时保持当前角度，不做任何事
    }

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(coverUrl)
            .crossfade(200)
            .build(),
        contentDescription = "专辑封面",
        modifier = modifier
            .clip(CircleShape)
            .graphicsLayer { rotationZ = rotation.value },
        contentScale = ContentScale.Crop
    )
}
```

### Step 5 [P0] — 替换 LyricsView 渐变实现，移除 Offscreen

**文件**: `feature/player/LyricsView.kt`

```kotlin
// 移除 CompositingStrategy.Offscreen + DstIn
// 改用 overlay 渐变层（从背景色到透明）
Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(lyrics, key = { index, _ -> index }) { index, line ->
            // ... 歌词文本
        }
    }

    // 顶部渐变遮罩
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .align(Alignment.TopCenter)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        Color.Transparent
                    )
                )
            )
    )

    // 底部渐变遮罩
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .align(Alignment.BottomCenter)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    )
}
```

### Step 6 [P1] — 统一图片加载策略

**新文件**: `feature/components/CoverImage.kt`（封装统一配图请求）

**修改文件**: `MediaListItem.kt`, `MiniPlayerBar.kt`, `HomeScreen.kt`, `PlaylistCard.kt`, `RotatingCover.kt`

```kotlin
@Composable
fun CoverImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(200)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
```

所有 `AsyncImage(model = xxx.coverUrl)` 替换为 `CoverImage(url = xxx.coverUrl)`。

### Step 7 [P1] — 全量替换 collectAsStateWithLifecycle

**修改文件**: 所有 Screen 文件

```kotlin
// Before:
val state by viewModel.state.collectAsState()

// After:
val state by viewModel.state.collectAsStateWithLifecycle()
```

涉及文件：
- `HomeScreen.kt`
- `SearchScreen.kt`
- `LibraryScreen.kt`
- `PlaylistDetailScreen.kt`
- `PlayerLyricsScreen.kt`
- `AppRoot.kt`

需添加依赖（如未有）：`androidx.lifecycle:lifecycle-runtime-compose`

### Step 8 [P1] — LazyList 添加 contentType + 补全 key

**修改文件**: 所有包含 LazyColumn/LazyVerticalGrid 的 Screen

```kotlin
// SearchScreen.kt / PlaylistDetailScreen.kt / LibraryScreen.kt
itemsIndexed(
    items = state.tracks,
    key = { _, t -> "${t.platform.id}:${t.id}" },
    contentType = { _, _ -> "track" }
) { index, track -> ... }

// 加载项
item(contentType = "loading") { LoadingView() }

// HomeScreen.kt LazyVerticalGrid
items(
    items = state.toplists,
    key = { it.id },
    contentType = { "toplist" }
) { toplist -> ... }
```

### Step 9 [P2] — 添加导航过渡动画

**文件**: `app/navigation/AppNavGraph.kt`

```kotlin
NavHost(
    navController = navController,
    startDestination = Routes.Home,
    modifier = modifier,
    enterTransition = { fadeIn(animationSpec = tween(200)) },
    exitTransition = { fadeOut(animationSpec = tween(150)) },
    popEnterTransition = { fadeIn(animationSpec = tween(200)) },
    popExitTransition = { fadeOut(animationSpec = tween(150)) }
) {
    // ... 路由定义不变
}
```

### Step 10 [P2] — 修复 MiniPlayerBar shadow 顺序

**文件**: `feature/player/MiniPlayerBar.kt`

```kotlin
// Before: .clip() 在 .shadow() 前
Surface(
    modifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(26.dp))
        .shadow(elevation = 6.dp, ...)
        .clickable(onClick = onClick),
    ...
)

// After: 使用 Surface 自带的 shape + shadowElevation
Surface(
    modifier = modifier
        .fillMaxWidth()
        .clickable(onClick = onClick),
    shape = RoundedCornerShape(26.dp),
    shadowElevation = 6.dp,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
) { ... }
```

## 关键文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `feature/player/PlayerViewModel.kt` | 修改 | 拆分 staticUiState + progressState 流 |
| `feature/player/PlayerLyricsScreen.kt` | 修改 | 按粒度订阅，分离 progress |
| `feature/player/PlayerControlsSection.kt` | 修改 | 接收拆分后的参数 |
| `feature/player/RotatingCover.kt` | 修改 | Animatable 替代 infiniteTransition 副作用 |
| `feature/player/LyricsView.kt` | 修改 | 移除 Offscreen，改 overlay 渐变 |
| `feature/player/MiniPlayerBar.kt` | 修改 | Surface shape/shadowElevation |
| `feature/player/FullScreenPlayer.kt` | 修改 | 适配拆分后的状态参数 |
| `feature/player/PlayerBottomSheet.kt` | 修改 | 适配拆分后的状态参数 |
| `feature/components/CoverImage.kt` | 新建 | 统一封面图加载组件 |
| `feature/components/MediaListItem.kt` | 修改 | 使用 CoverImage |
| `feature/components/PlaylistCard.kt` | 修改 | 使用 CoverImage |
| `feature/home/HomeScreen.kt` | 修改 | CoverImage + contentType + key + collectAsStateWithLifecycle |
| `feature/search/SearchScreen.kt` | 修改 | contentType + collectAsStateWithLifecycle |
| `feature/playlist/PlaylistDetailScreen.kt` | 修改 | contentType + collectAsStateWithLifecycle |
| `feature/library/LibraryScreen.kt` | 修改 | collectAsStateWithLifecycle |
| `app/navigation/AppNavGraph.kt` | 修改 | 添加 fade 过渡动画 |
| `app/AppRoot.kt` | 修改 | collectAsStateWithLifecycle |

## 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| 状态拆分后静态信息与进度短暂不同步 | 两个流来自同一 stateStore，用 WhileSubscribed(5000) 保持活跃 |
| RotatingCover 动画效果变化 | Animatable 方案视觉效果等价，暂停时保持角度 |
| 图片 placeholder 资源缺失 | CoverImage 暂不设 placeholder drawable，仅用 crossfade 过渡 |
| collectAsStateWithLifecycle 恢复时进度跳变 | 属预期行为，用户回到前台自动同步 |
| LyricsView 渐变视觉差异 | overlay 方案需要读取当前页面背景色 |

## SESSION_ID（供 /ccg:execute 使用）
- CODEX_SESSION: 019cb825-e7da-7bc1-8975-3dc13b9deb0c
- GEMINI_SESSION: ef732b62-5ce7-46ee-9e4f-67e74a372596
