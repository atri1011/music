[根目录](../../../../CLAUDE.md) > [app](../../../) > [data](../) > **data**

# Data 模块

## 模块职责

实现 domain 层定义的 Repository 接口，封装网络请求、数据库操作、缓存策略。

## 入口与启动

通过 Hilt DI (`RepositoryModule`) 绑定接口到实现类，随 `SingletonComponent` 生命周期存活。

## 对外接口

### OnlineMusicRepositoryImpl

核心在线音乐仓库，现已收敛为**编排层**，负责聚合多个在线数据协作者:
- **搜索/热词/联想**: 由 `online/OnlineMusicSearchDelegate` 处理
- **榜单**: 获取榜单列表 + 详情，支持日缓存 (`HomeContentCacheStore`)
- **歌单**: 网易云直连 API (`getNeteasePlaylistDetailV6`)，其他平台走 Dispatch
- **解析播放/歌词/MV/分享链接**: 由 `online/OnlineMusicMediaResolver` 处理
- **封面补全**: 由 `online/OnlineTrackCoverEnricher` 统一处理三平台补全与缓存

关键设计:
- QQ 音乐榜单列表有直连 fallback (`postQqMusicu`)
- 网易云歌单详情使用原生 API 而非 Dispatch，因需要完整 trackIds 分批补全
- 播放 URL 中 QQ 域名自动升级 HTTP -> HTTPS

### LocalLibraryRepositoryImpl

本地数据仓库，封装 5 个 DAO 操作:
- 收藏: CRUD + 批量收藏状态查询 (`applyFavoriteState`)
- 最近播放: 带 play_count 累加的 upsert + 自动清理 (保留 100 条)
- 歌单: UUID 生成 ID + 排序管理
- 歌词缓存: key = "platform:songId" / "platform:songId:trans"

### RecommendationRepositoryImpl

推荐引擎:
- **每日推荐**: 基于收藏 + 最近播放构建 TasteSeed 评分体系，综合考虑收藏位置、播放频率、忠诚度（首次播放距今天数），搜索相似歌曲去重后输出
- **私人 FM**: 随机最近播放记录 -> 收藏 fallback
- **推荐歌单**: 网易云个性化歌单 API，日缓存
- **相似歌曲**: 按歌手搜索同平台

### DTO 层 (data/remote/dto)

| DTO | 说明 |
|-----|------|
| `ParseRequestDto` | 解析请求: platform, ids, quality |
| `ParseResponseDto` | 解析响应: code, msg, data (JsonElement) |
| `MethodsTemplateDto` | 方法下发响应: code, msg, data (MethodsDataDto) |
| `MethodsDataDto` | 方法配置: url, method, headers, params, body, transform |
| `TransformRuleDto` | 转换规则: root (JSON path), fields (字段映射) |

## 关键依赖与配置

- TuneHubApi (Retrofit)
- DispatchExecutor (网络 Dispatch 引擎)
- Room DAOs (本地存储)
- HomeContentCacheStore (DataStore 日缓存)
- Json (kotlinx-serialization)

## 测试与质量

| 测试文件 | 覆盖范围 |
|---------|---------|
| `OnlineMusicRepositoryImplTest.kt` | 在线搜索/榜单/解析逻辑 |
| `RecommendationRepositoryImplTest.kt` | 推荐算法/评分逻辑 |

缺口: `LocalLibraryRepositoryImpl` 无测试。

## 常见问题 (FAQ)

**Q: 为什么网易云歌单不走 Dispatch?**
A: 网易云歌单 API 返回的 tracks 可能不完整（仅含部分字段），需要通过 trackIds 分批调用 songDetail 补全，逻辑较复杂无法用通用模板表达。

**Q: 封面缓存如何工作?**
A: 每个平台维护一个 `ConcurrentHashMap<songId, coverUrl>` 内存缓存，搜索/榜单返回的 Track 如果缺封面，批量调用平台 songDetail API 补全并缓存。

## 相关文件清单

```
app/src/main/java/com/music/myapplication/data/
  remote/dto/
    ParseDto.kt, MethodsTemplateDto.kt
  repository/
    OnlineMusicRepositoryImpl.kt, LocalLibraryRepositoryImpl.kt, RecommendationRepositoryImpl.kt
    online/
      OnlineMusicSearchDelegate.kt, OnlineMusicMediaResolver.kt, OnlineTrackCoverEnricher.kt
```

## 变更记录 (Changelog)

| 日期 | 操作 | 说明 |
|------|------|------|
| 2026-03-08 | 初始化 | 初次生成文档 |
