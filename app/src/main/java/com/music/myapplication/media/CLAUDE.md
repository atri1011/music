[根目录](../../../../CLAUDE.md) > [app](../../../) > [media](../) > **media**

# Media 模块

## 模块职责

封装 Media3 (ExoPlayer) 音频播放引擎，管理播放队列、播放模式、播放状态、MediaSession 服务。

## 入口与启动

- `MusicPlaybackService` -- Android 前台服务，在 AndroidManifest 中注册，foregroundServiceType = mediaPlayback
- `MediaControllerConnector` -- ViewModel 层通过此连接器与 Service 通信

## 对外接口

### MediaControllerConnector (媒体控制器)
```kotlin
fun connect()                                   // 建立 MediaSession 连接
fun disconnect()                                // 断开连接
fun playTrack(track, queue, index)              // 设置队列 + 播放指定曲目
fun play() / pause()                            // 播放/暂停
fun seekTo(positionMs)                          // 跳转进度
fun skipToNext(track) / skipToPrevious(track)   // 上下首 (需外部预解析)
fun stop()                                      // 停止 + 重置状态
```

### PlaybackStateStore (状态中心)
全局唯一的播放状态 `StateFlow<PlaybackState>`:
- `updateTrack / updatePlaying / updatePosition / updateDuration / updatePlaybackMode / updateQueue / updateQuality / reset`
- 所有更新带 diff 检查，避免不必要的 emit

### QueueManager (队列管理)
内存播放队列:
- `setQueue(tracks, startIndex)` -- 设置队列
- `moveToNext / moveToPrevious / moveToIndex` -- 移动指针
- `addToQueue / removeFromQueue / clear` -- 增删

### PlaybackModeManager (播放模式)
- `setMode / toggleMode` -- 设置/切换模式
- `getNextTrack / getPreviousTrack` -- 根据当前模式获取下一首
  - SEQUENTIAL: 队列顺序
  - REPEAT_ONE: 返回当前
  - SHUFFLE: Fisher-Yates 洗牌序列

## 关键依赖与配置

- Media3 ExoPlayer: 通过 `MediaModule` Hilt 注入 Singleton
- AudioAttributes: CONTENT_TYPE_MUSIC + USAGE_MEDIA
- 位置更新: 500ms 间隔轮询
- MediaSession: 支持系统通知栏/锁屏控制

## 数据模型

播放状态流转:
```
PlayerViewModel.playTrack()
  -> resolvePlayableUrl() (网络)
  -> MediaControllerConnector.playTrack()
    -> QueueManager.setQueue()
    -> PlaybackStateStore.updateTrack/Queue()
    -> ExoPlayer.setMediaItem() + prepare() + play()
  -> MusicPlaybackService.playerListener
    -> PlaybackStateStore.updatePlaying/Duration/Position()
    -> 曲目结束: PlaybackModeManager.getNextTrack() -> 自动播放下一首
```

## 测试与质量

无测试。建议优先测试:
- `QueueManager` -- 纯逻辑，易于单元测试
- `PlaybackModeManager` -- 模式切换 + shuffle 逻辑

## 相关文件清单

```
app/src/main/java/com/music/myapplication/media/
  player/
    QueueManager.kt, PlaybackModeManager.kt
  service/
    MusicPlaybackService.kt
  session/
    MediaControllerConnector.kt
  state/
    PlaybackStateStore.kt
```

## 变更记录 (Changelog)

| 日期 | 操作 | 说明 |
|------|------|------|
| 2026-03-08 | 初始化 | 初次生成文档 |
