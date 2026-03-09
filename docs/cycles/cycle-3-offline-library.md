# 周期 3 开发文档 — 离线与资料库能力落地

> **项目**: My Application — Android 音乐播放器  
> **周期定位**: 周期 3 / 建议 2 周  
> **对应总文档**: `docs/technical-implementation.md`

---

## 1. 周期目标

本周期把播放器从“纯在线播放器”升级为“具备本地资料库和离线能力的音乐应用”，重点落在下载、离线播放、本地音乐扫描、缓存管理和断点续播。

---

## 2. 范围定义

### 2.1 前端范围

| 模块 | 任务 | 结果 |
|------|------|------|
| 下载 | 歌曲下载 / 离线缓存 | 支持下载任务、已下载列表、离线优先播放 |
| 本地库 | 本地音乐扫描 | 支持 `MediaStore` 扫描并入库 |
| 设置页 | 缓存管理 | 展示缓存体积并支持清理 |
| 播放服务 | 断点续播 | 恢复上次曲目、队列与进度 |
| 歌单 | 歌单内歌曲管理 | 支持删除、拖拽排序、持久化顺序 |

### 2.2 后端范围

| 模块 | 任务 | 结果 |
|------|------|------|
| 播放解析 | 下载地址策略确认 | 明确下载场景的地址有效期和失败策略 |
| 音质策略 | 下载默认质量确认 | 明确默认下载音质和可选质量 |
| 平台兼容 | 无新增服务端存储 | 本周期以接口策略确认为主 |

---

## 3. 前端开发清单

### 3.1 歌曲下载 / 离线缓存

**涉及文件**

- `core/download/DownloadManager.kt`
- `core/download/DownloadWorker.kt`
- `core/database/entity/DownloadedTrackEntity.kt`
- `core/database/dao/DownloadedTracksDao.kt`
- `feature/library/DownloadedScreen.kt`
- `di/DownloadModule.kt`

**开发要求**

- 使用 `WorkManager` 承载下载任务
- 下载状态至少区分：下载中、成功、失败
- 播放时优先查询本地已下载文件
- 下载通知需要可见，避免后台任务悄悄挂掉

### 3.2 本地音乐扫描

**涉及文件**

- `core/local/LocalMusicScanner.kt`
- `core/database/entity/LocalTrackEntity.kt`
- `core/database/dao/LocalTracksDao.kt`
- `feature/library/LocalMusicScreen.kt`

**开发要求**

- 扫描 `MediaStore.Audio.Media`
- 过滤 `IS_MUSIC == 1` 且时长大于 30 秒
- 支持增量更新，避免重复写入
- `Platform` 增加 `LOCAL` 后，相关调用链同步调整

### 3.3 缓存管理

**涉及文件**

- `core/cache/CacheManager.kt`
- `MoreScreen.kt`
- `MoreViewModel.kt`

**开发要求**

- 统计图片缓存、歌词缓存、模板缓存
- 支持一键清理
- 清理前后 UI 数值变化要准确

### 3.4 断点续播

**涉及文件**

- `PlayerPreferences.kt`
- `PlaybackControlStateHolder.kt`
- `MusicPlaybackService.kt`

**开发要求**

- 保存当前歌曲、播放位置、队列和当前索引
- 应用重启后恢复静态状态，但不强制自动播放
- 用户再次点击播放时能从上次进度恢复

### 3.5 歌单内歌曲管理

**涉及文件**

- `PlaylistDetailScreen.kt`
- `PlaylistDetailViewModel.kt`
- `PlaylistSongsDao.kt`
- `LocalLibraryRepositoryImpl.kt`

**开发要求**

- 仅对本地歌单开放编辑模式
- 支持拖拽排序、删除歌曲、批量提交顺序
- 编辑退出后顺序必须持久化

---

## 4. 后端开发清单

### 4.1 下载场景接口约定

**需要确认**

- `resolvePlayableUrl` 返回的 URL 是否允许直接下载
- 下载 URL 是否存在短时效签名
- 下载失败是否允许前端重新解析后重试

### 4.2 音质与版权约定

**需要确认**

- 下载是否只支持固定音质
- 各平台是否存在版权限制导致不可下载
- 前端是否需要展示“可播放但不可下载”的状态

---

## 5. 数据库与存储变更

### 5.1 Room 迁移

- 从 `v2` 升级到 `v3`
- 新增表：
  - `downloaded_tracks`
  - `local_tracks`

### 5.2 DataStore 变更

- 新增断点续播相关字段
- 继续保留播放模式、外观、网络偏好类配置

### 5.3 迁移要求

- 移除 `fallbackToDestructiveMigration(dropAllTables = true)`
- 改为显式 `Migration(2, 3)`
- 升级流程必须验证历史数据不丢失

---

## 6. 周内安排建议

| 时间 | 前端重点 | 后端重点 | 联调重点 |
|------|----------|----------|---------|
| 第 1 周前半 | 数据库迁移、下载基础设施 | 下载 URL 策略确认 | 下载场景契约确认 |
| 第 1 周后半 | 本地扫描、断点续播 | 音质和版权约定 | 离线播放链路联调 |
| 第 2 周前半 | 缓存管理、歌单编辑 | 异常策略补齐 | 断点续播和本地库联调 |
| 第 2 周后半 | 回归和缺陷修复 | 兼容性确认 | 周期整体验收 |

---

## 7. 联调清单

- 下载完成后断网也能播放
- 本地音乐扫描后，列表不重复、不丢项
- 断点续播恢复的曲目、队列、进度位置一致
- `LOCAL` 平台不会误走线上解析链路
- 缓存清理前后体积数值正确

---

## 8. 验收标准

- 下载列表、已下载播放、本地音乐列表三者链路完整
- `Migration(2, 3)` 通过真实升级验证
- 断点续播支持应用退出后恢复
- 本地歌单编辑后的顺序刷新后不丢失

---

## 9. 风险与依赖

- Android 版本差异会影响媒体权限和存储路径
- `Platform.LOCAL` 是系统性改动，别只改枚举不改调用链
- 下载和本地文件播放都容易踩 URI 和权限坑，需要真机回归

---

## 10. 周期产出物

- 可用的下载与离线播放能力
- 可维护的本地音乐资料库
- 可恢复的断点续播能力
- 可编辑的本地歌单管理能力
