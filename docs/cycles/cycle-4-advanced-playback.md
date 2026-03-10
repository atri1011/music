# 周期 4 开发文档 — 高级播放体验增强

> **项目**: My Application — Android 音乐播放器  
> **周期定位**: 周期 4 / 建议 2 周  
> **对应总文档**: `docs/technical-implementation.md`

---

## 1. 周期目标

本周期聚焦“听感”和“展示感”的增强能力，把播放器从基础可用推进到具备更强差异化体验，包括倍速、均衡器、Crossfade、歌词海报和歌单封面自定义。

---

## 2. 范围定义

### 2.1 前端范围

| 模块 | 任务 | 结果 |
|------|------|------|
| 播放控制 | 播放速度调节 | 支持 `0.5x ~ 2.0x` 倍速和状态持久化 |
| 音效 | 均衡器 / 音效 | 提供预设 EQ 和自定义调节 |
| 播放服务 | Crossfade 无缝切歌 | 提供开关和可配置淡入淡出时长 |
| 歌单 | 歌单封面自定义 | 支持本地图片选取和持久化 |
| 分享 | 歌词海报 / 歌词分享 | 支持歌词海报生成、预览和分享 |

### 2.2 后端范围

| 模块 | 任务 | 结果 |
|------|------|------|
| 音质元数据 | 可选补充 | 支撑更明确的质量展示 |
| 媒体元数据 | 可选补充 | 为海报和扩展展示补充字段 |
| 接口配合 | 联调支撑 | 本周期以后端配合联调为主 |

---

## 3. 前端开发清单

### 3.1 播放速度调节

**涉及文件**

- `MediaControllerConnector.kt`
- `PlaybackControlStateHolder.kt`
- `PlaybackState.kt`
- `PlaybackStateStore.kt`

**开发要求**

- 当前倍速值需要进入全局播放状态
- 支持通过菜单快速切换常用档位
- 重启后恢复上次速度配置

**当前实现约定**

- 倍速状态已进入 `PlaybackState` / `PlaybackStateStore`，播放器更多菜单通过 `SpeedPickerSheet` 提供 `0.5x ~ 2.0x` 常用档位
- `PlayerPreferences` 使用 DataStore 持久化倍速，`PlaybackControlStateHolder` 绑定时会将上次速度同步回 `MediaControllerConnector`
- 播放服务监听 `PlaybackParameters` 变化并回写全局状态，保证 UI 显示、状态存储和实际播放速度一致

### 3.2 均衡器 / 音效

**涉及文件**

- `media/audio/EqualizerManager.kt`
- `feature/player/EqualizerScreen.kt`
- `core/datastore/EqualizerPreferences.kt`
- `MusicPlaybackService.kt`

**开发要求**

- 先支持预设 EQ，再扩展自定义频段
- 绑定 `audioSessionId` 后才能初始化 EQ
- 若设备不支持 `Equalizer`，需要提供禁用提示

**当前实现约定**

- 已支持预设 EQ 切换和自定义频段拖动；进入自定义调节时，将 `presetIndex` 置为 `-1` 并持久化各频段增益
- `MusicPlaybackService` 在 `audioSessionId` 可用时绑定 `EqualizerManager`，播放器就绪和 `audioSessionId` 变化时都会重新应用 EQ 配置
- `EqualizerScreen` 对不支持 `Equalizer` 的设备直接展示禁用提示卡片，避免用户开关点了个寂寞

### 3.3 Crossfade 无缝切歌

**涉及文件**

- `MusicPlaybackService.kt`
- `MediaModule.kt`

**开发要求**

- 本功能必须先做 POC，再决定正式方案
- 支持显式开关，关闭时退回普通切歌
- 若采用双 Player 方案，必须确认资源释放和状态同步策略

**当前实现约定**

- 当前为单 `ExoPlayer` 的 fade-through POC，并未上双 Player 方案，先把行为边界摸实再说
- 设置页已提供 `Crossfade（POC）` 开关和 `500ms ~ 4000ms` 时长调节，配置由 `PlayerPreferences` 持久化
- 关闭 Crossfade 时，播放服务会取消过渡任务并恢复音量到 `1f`，避免残留淡入淡出副作用
- 显式切歌采用 `FADE_THROUGH / DIRECT` 两种路径；自然播完切到下一首时采用 `FADE_IN_ONLY`，保证 POC 行为稳定可控

### 3.4 歌单封面自定义

**涉及文件**

- `PlaylistEntity.kt`
- `LibraryScreen.kt`
- `LibraryViewModel.kt`
- `LocalLibraryRepositoryImpl.kt`

**开发要求**

- 支持从相册选择图片
- 选中的图片复制到应用私有目录
- 列表页和详情页显示保持一致

**当前实现约定**

- 资料库页本地歌单已提供封面更换入口，使用系统图片选择器选图
- `LocalLibraryRepositoryImpl` 会把选中的图片复制到应用私有目录 `files/playlist_covers`，并在替换封面时清理旧的托管文件
- 歌单详情页直接复用本地歌单持久化后的 `coverUrl`，列表页和详情页展示链路保持一致

### 3.5 歌词海报 / 歌词分享

**涉及文件**

- `feature/player/LyricsPosterGenerator.kt`
- `feature/player/LyricsPosterDialog.kt`

**开发要求**

- 长按歌词行触发海报生成
- 至少提供 2 种模板
- 支持保存和分享

**当前实现约定**

- 歌词页已支持长按歌词行打开 `LyricsPosterDialog`，海报预览生成失败时给出页内提示
- 当前内置 `流光`、`留白` 两种模板，满足本周期最小模板数要求
- 保存走系统相册 / `Pictures/MyApplication`（Android Q 及以上），分享走 `FileProvider`，兼顾保存和外部分享链路

---

## 4. 后端开发清单

### 4.1 音质与元数据配合

**需要确认**

- 是否能返回更细粒度的音质标签
- 是否能返回适合作为海报展示的补充元信息
- 若无新增字段，也需要确认当前字段是否足够支撑展示

---

## 5. 周内安排建议

| 时间 | 前端重点 | 后端重点 | 联调重点 |
|------|----------|----------|---------|
| 第 1 周前半 | 倍速、EQ 基础能力 | 元数据可用性确认 | 倍速与音效状态联调 |
| 第 1 周后半 | 歌单封面、歌词海报 | 配套字段确认 | 展示链路联调 |
| 第 2 周前半 | Crossfade POC 与集成 | 风险评估配合 | Crossfade 行为验证 |
| 第 2 周后半 | 回归和缺陷修复 | 联调支持 | 周期整体验收 |

---

### 5.1 第 2 周后半收口记录（2026-03-10）

- 已完成周期 4 现有实现核对，确认倍速、EQ、Crossfade POC、歌单封面自定义、歌词海报链路均已落到代码
- 已执行针对性单元测试回归：
  - `./gradlew.bat :app:testDebugUnitTest --tests "com.music.myapplication.data.repository.LocalLibraryRepositoryImplTest" --tests "com.music.myapplication.feature.library.LibraryViewModelTest" --tests "com.music.myapplication.feature.playlist.PlaylistDetailViewModelTest"`
  - 结果：`BUILD SUCCESSFUL`
- 本轮重点确认：
  - 本地歌单封面持久化后，详情页继续复用同一 `coverUrl`
  - 本地歌单编辑提交失败时保留编辑态并展示页内错误
  - QQ 榜单封面 hydration 为异步补全，不阻塞首屏曲目列表展示
- 仍需上线前真机补验的项：
  - `Equalizer` 设备兼容性和 `audioSessionId` 绑定稳定性
  - Crossfade 的实际听感、切歌边界和后台切回场景
  - 歌词海报的内存占用、清晰度与分享兼容性

## 6. 联调清单

- 倍速切换后 UI、状态、实际播放速度一致
- EQ 切换后听感变化可验证，重启后状态保留
- Crossfade 开关行为明确，关闭后不残留副作用
- 海报生成不出现空白图、错位图、低清图

**当前联调结论**

- 代码链路和单测回归已完成，基础逻辑没掉链子
- 真机联调仍以 EQ、Crossfade、海报三类体验型能力为主，别拿模拟器听感当结论

---

## 7. 验收标准

- 倍速和 EQ 能稳定工作并可持久化
- Crossfade 至少完成 POC 级稳定验证，达到上线标准才进入正式发布
- 歌词海报支持保存和分享
- 歌单封面修改后能长期保留

**当前验收状态**

- 倍速、歌单封面、歌词海报保存/分享链路已具备提交验收条件
- EQ 和 Crossfade 已完成代码侧收口，但是否进入正式发布，仍以真机验证结果为准

---

## 8. 风险与依赖

- `Equalizer` 设备兼容性差异较大，必须做真机验证
- `Crossfade` 复杂度高，别一拍脑门就大改 `MusicPlaybackService`
- 海报生成涉及图片内存占用，需要防 OOM

---

## 9. 周期产出物

- 倍速控制能力
- EQ 与音效能力
- Crossfade 方案验证结果
- 歌单封面与歌词海报能力
