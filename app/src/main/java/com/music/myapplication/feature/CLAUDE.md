[根目录](../../../../CLAUDE.md) > [app](../../../) > [feature](../) > **feature**

# Feature 模块

## 模块职责

UI 表现层，包含所有页面的 Screen (Composable) 和 ViewModel。每个子目录对应一个功能页面或共享组件集。

## 入口与启动

所有页面通过 `AppNavGraph.kt` 注册路由，由 Navigation Compose 管理生命周期。

## 子模块详解

### feature/home (首页)

**HomeScreen.kt** -- 双 Tab 布局:
- Tab 0 "推荐": 每日推荐歌曲横滑列表 + 私人 FM 卡片 + 推荐歌单网格
- Tab 1 "排行榜": 平台切换 (网易/QQ/酷我) + 榜单卡片 (带前 3 首预览)
- 顶部搜索入口

**HomeViewModel.kt** -- 状态: `HomeUiState`
- `loadToplists(platform)` -- 加载榜单列表 + 并行预加载前 3 首
- `loadRecommendations()` -- 加载每日推荐/FM/推荐歌单

### feature/search (搜索)

**SearchScreen.kt** -- 搜索界面，支持平台切换、分页加载更多
**SearchViewModel.kt** -- 状态: `SearchUiState`
- 400ms debounce 自动搜索
- 记住用户选择的平台 (DataStore)
- 搜索结果自动标注收藏状态

### feature/library (我的)

**LibraryScreen.kt** -- 我的页面: 收藏列表 + 自建歌单 + 最常播放 + 播放统计 (总次数/总时长)
**LibraryViewModel.kt** -- 状态: `LibraryUiState`
- 5 个 Flow combine 实时更新
- 歌单创建/删除

### feature/player (播放器) -- 最大子模块

| 文件 | 职责 |
|------|------|
| `PlayerViewModel.kt` | 全局播放控制 VM (全应用共享实例) |
| `FullScreenPlayer.kt` | 全屏播放器 UI (封面/控制/进度) |
| `MiniPlayerBar.kt` | 底部迷你播放条 |
| `PlayerBottomSheet.kt` | 播放器底部弹出面板 |
| `PlayerControlsSection.kt` | 播放控制按钮组 |
| `PlayerLyricsScreen.kt` | 播放器 + 歌词联合页面 |
| `LyricsView.kt` | 歌词滚动视图 |
| `LyricsParser.kt` | LRC 歌词解析器 (支持翻译合并) |
| `LyricsUiState.kt` | 歌词 UI 状态定义 |
| `RotatingCover.kt` | 旋转封面动画 |

**PlayerViewModel** 核心功能:
- `playTrack(track, queue, index)` -- 解析 URL -> 播放 -> 记录
- `skipNext/skipPrevious` -- 基于 PlaybackModeManager 决定下一首
- `togglePlaybackMode` -- 顺序/随机/单曲循环
- `toggleFavorite` -- 收藏切换
- `setQuality` -- 音质切换 (128k/320k/flac)
- 自动加载歌词 (本地缓存优先 -> 网络获取)
- 自动加载曲目信息 (播放次数/首次播放日期/相似歌曲)
- QQ 音乐数字 ID 兼容兜底 (搜索 mid 重试)

**UI 状态拆分** (性能优化):
- `MiniPlayerUiState` -- 迷你播放条所需最小数据
- `PlayerStaticUiState` -- 非频繁变化的播放器状态
- `PlaybackProgressUiState` -- 高频进度更新
- `TrackActionUiState` -- 解析中状态
- `TrackInfoUiState` -- 曲目额外信息

### feature/playlist (歌单详情)

**PlaylistDetailScreen.kt** -- 歌单/榜单详情页
**PlaylistDetailViewModel.kt** -- 支持 toplist/playlist/local 三种来源，QQ/酷我 榜单有异步封面 hydration

### feature/discover (发现)

**DiscoverScreen.kt** -- 发现页 (搜索入口)

### feature/more (更多)

**MoreScreen.kt** + **MoreViewModel.kt** -- 设置页面，当前仅 API Key 管理

### feature/components (共享组件)

| 组件 | 说明 |
|------|------|
| `MediaListItem.kt` | 歌曲列表项 |
| `PlaylistCard.kt` | 歌单/榜单卡片 |
| `CoverImage.kt` | 封面图片 (Coil + 占位) |
| `PlatformFilterChips.kt` | 平台筛选 Chips |
| `QualitySelector.kt` | 音质选择器 |
| `LoadingView.kt` | 加载中占位 |
| `ErrorView.kt` | 错误提示 + 重试 |
| `ShimmerEffect.kt` | 骨架屏闪烁效果 |

## 关键依赖与配置

- Hilt ViewModel 注入 (`@HiltViewModel`)
- Navigation Compose type-safe routes (`@Serializable`)
- `collectAsStateWithLifecycle` 生命周期感知
- Coil `AsyncImage` 图片加载

## 测试与质量

| 测试文件 | 覆盖范围 |
|---------|---------|
| `SearchViewModelTest.kt` | 搜索: debounce、平台切换、分页 |
| `PlaylistDetailViewModelTest.kt` | 歌单详情: toplist/playlist 加载 |

缺口: `PlayerViewModel`、`HomeViewModel`、`LibraryViewModel`、`MoreViewModel` 无测试。

## 相关文件清单

```
app/src/main/java/com/music/myapplication/feature/
  home/       HomeScreen.kt, HomeViewModel.kt
  search/     SearchScreen.kt, SearchViewModel.kt
  library/    LibraryScreen.kt, LibraryViewModel.kt
  player/     PlayerViewModel.kt, FullScreenPlayer.kt, MiniPlayerBar.kt, PlayerBottomSheet.kt,
              PlayerControlsSection.kt, PlayerLyricsScreen.kt, LyricsView.kt, LyricsParser.kt,
              LyricsUiState.kt, RotatingCover.kt
  playlist/   PlaylistDetailScreen.kt, PlaylistDetailViewModel.kt
  discover/   DiscoverScreen.kt
  more/       MoreScreen.kt, MoreViewModel.kt
  components/ MediaListItem.kt, PlaylistCard.kt, CoverImage.kt, PlatformFilterChips.kt,
              QualitySelector.kt, LoadingView.kt, ErrorView.kt, ShimmerEffect.kt
```

## 变更记录 (Changelog)

| 日期 | 操作 | 说明 |
|------|------|------|
| 2026-03-08 | 初始化 | 初次生成文档 |
