# Apple Music / Cupertino 全局高复刻改造设计

- 日期: 2026-04-08
- 适用范围: `Theme.kt` / `AppRoot.kt` / `AppNavGraph.kt` / `feature/home` / `feature/search` / `feature/library` / `feature/more` / `feature/player` / `VideoPlayerScreen`
- 当前阶段: 设计确认，未进入实现

## 1. 背景

当前项目已经具备完整的音乐业务能力，但 UI 语言仍然是多种风格混用:

- 主题入口仍以 `Theme.kt` 中的 QQ 绿 + Material `colorScheme` 为主
- 应用壳层在 `AppRoot.kt` 中仍直接使用 `NavigationBar` / `NavigationBarItem`
- `MiniPlayerBar`、播放器、搜索页、设置页、首页等页面虽有部分玻璃与 token 沉淀，但整体并未形成统一语法
- 页面之间仍存在明显的 Material 默认气质、旧玻璃语言、局部渐变表达并存的情况

这导致当前产品的问题不是“单页不好看”，而是**全站不像一个统一设计系统下的产品**:

- 首页、搜索、资料库、更多、播放器的第一眼气质不一致
- 壳层与页面的关系松散，底栏 / mini player / 页面内容各算各的 inset
- 主题 token 已有基础，但仍不足以承接高复刻的 Apple / Cupertino 语法
- 如果继续逐页硬改，最终只会得到一组“各自像点 Apple”的页面，而不是一整个 Apple 风应用

## 2. 改造目标

本次改造目标:

1. 以 Apple Music / iOS 系统应用为主参照，完成一次全站级视觉和交互气质重构
2. 采用“壳层先行”路线，先统一 design system 与 app chrome，再迁移核心页面
3. 允许对顶层入口和页面组织做轻度重排，但不改变业务能力、播放内核和数据链路
4. 通过共享 Apple primitives 承接页面迁移，避免页面内散落写颜色、圆角、模糊和分隔线规则
5. 在整体 Apple 化的前提下，保留少量柔和玻璃和轻渐变作为项目自身口音，但不能抢主语

## 3. 已确认边界

### 3.1 In Scope

- 重建主题 token、共享视觉规则和 Cupertino primitives
- 改造 `AppRoot` / `AppNavGraph` / 底部壳层 / mini player / 页面转场
- 迁移 `Home` / `Search` / `Library` / `More`
- 迁移 `FullScreenPlayer` / `MiniPlayer` / `Lyrics` / `VideoPlayer`
- 统一 `Dialog` / `Sheet` / `Menu` / grouped list / search field 的视觉与交互语法

### 3.2 Out of Scope

- 不改 `ViewModel` 的业务语义
- 不改播放解析、下载、收藏、评论、歌词、更新等核心能力边界
- 不改主导航机制本身，只调整壳层和页面容器
- 不做“页面局部高仿 + 全站其余照旧”的半套方案
- 不把 `CyreneMusic` 当作逐像素复制目标

## 4. 用户已确认的产品方向

本设计基于以下已确认决策:

1. 总体方向为 **Apple Music / Cupertino 高复刻**
2. 实施路径采用 **壳层先行**
3. 顶层入口采用 **四栏**: `Home / Search / Library / More`
4. `More` 定位为 **纯设置中心**
5. `Home` 采用 **编辑流优先**
6. `Library` 采用 **系统式资料库**
7. `Search` 采用 **系统搜索页**
8. `MiniPlayer` 采用 **悬浮胶囊卡片**
9. `FullScreenPlayer` 采用 **克制版 Apple Music**
10. 现有 glass / 渐变语言 **保留一点神韵**，但 Apple 为主语

## 5. 当前实现锚点

### 5.1 主题与 token

- `app/src/main/java/com/music/myapplication/ui/theme/Theme.kt`
  - 当前仍以 Material `lightColorScheme` / `darkColorScheme` 为入口
  - 主题主色仍明显受 QQ 绿主导
  - 已存在 `GlassColors` / `LocalGlassColors`
- `app/src/main/java/com/music/myapplication/ui/theme/UiTokens.kt`
  - 已有 `AppSpacing` / `AppShapes` / `AppElevation` / `AppIconSize`
  - 适合作为 Apple token 迁移起点，但尚不足以表达 grouped background、floating chrome、Cupertino nav 等语义
- `app/src/main/java/com/music/myapplication/ui/theme/GlassModifiers.kt`
  - 已有 `appPremiumBackground()` 等背景与材质能力
  - 可作为“少量保留项目口音”的底层素材，但不能继续作为全站主视觉语法

### 5.2 应用壳层

- `app/src/main/java/com/music/myapplication/MainActivity.kt`
  - 已使用 `enableEdgeToEdge`，这是继续统一系统栏行为的正确入口
- `app/src/main/java/com/music/myapplication/app/AppRoot.kt`
  - 当前直接组合 `AppNavGraph + MiniPlayerContainer + NavigationBar`
  - 底栏仍是 Material `NavigationBar`
  - mini player 与底栏的层级关系仍是手工拼装，不是共享 scaffold 统一计算
- `app/src/main/java/com/music/myapplication/app/navigation/AppNavGraph.kt`
  - 路由入口稳定，可继续复用
  - 页面切换动画已存在，但风格仍非 Cupertino 基线

### 5.3 页面锚点

- `feature/home/HomeScreen.kt`
- `feature/search/SearchScreen.kt`
- `feature/library/LibraryScreen.kt`
- `feature/more/MoreScreen.kt`
- `feature/player/MiniPlayerBar.kt`
- `feature/player/FullScreenPlayer.kt`
- `feature/player/PlayerLyricsScreen.kt`
- `feature/video/.../VideoPlayerScreen.kt` 或现有 `VideoPlayerScreen` 所在文件

这些页面可以重做结构和视觉，但业务入口、状态来源和播放链路不应被重新定义。

## 6. 方案对比

### 方案 A: 一次性全站翻壳

先做 token，再并行改全部核心页面。

优点:

- 整体见效最快
- 短时间内就能看到“像 Apple” 的全局视觉结果

缺点:

- 回归风险最大
- 壳层规范未立稳时，页面会各自发明实现
- 极易产生风格漂移

### 方案 B: 壳层先行、分波迁移

先重建主题与 app chrome，再分波迁移页面与播放器。

优点:

- 最符合本次“全站高复刻”的本质诉求
- 风格一致性最好
- 共享 primitives 的复用价值最高

缺点:

- 前期视觉收益主要集中在骨架
- 需要更强的结构纪律

### 方案 C: Home + Player 样板间先行

先做最显眼的首页和播放器，再反推全站规范。

优点:

- 早期效果最强

缺点:

- 局部方案容易反客为主
- 后续补壳层时返工概率高

## 7. 推荐方案

采用 **方案 B: 壳层先行、分波迁移**。

原因:

- 这次目标是“全站像一个产品”，不是做几个高仿页面
- 只有先统一 theme / scaffold / nav / grouped primitives，后续页面才不会各写各的
- 当前仓库 `Theme.kt`、`UiTokens.kt`、`AppRoot.kt` 已具备改造抓手，适合从共享层下刀

## 8. 总体架构设计

### 8.1 新的共享 UI 能力层

建议在共享 UI 层新增以下抽象:

- `AppleThemeTokens`
- `AppleSurfaceStyle`
- `AppleScaffoldState`
- `AppleNavigationSpec`
- `AppleSheetSpec`

这些接口负责定义:

- 页面背景层级
- 分组背景 / 卡片背景 / 浮动 chrome 材质
- large title 与 compact nav 的行为
- 底栏 / mini player / snackbar / safe area 的统一 inset
- sheet / dialog / menu 的视觉与交互参数

### 8.2 新的 Cupertino primitives

至少新增:

- `AppleScaffold`
- `AppleNavigationBar`
- `AppleTabBar`
- `AppleSearchField`
- `AppleGroupedSection`
- `AppleListRow`
- `AppleSheet`
- `AppleDialog`

页面层只消费这些封装，不直接散落写:

- 圆角
- 背景 alpha
- 分隔线缩进
- blur 半径
- 底部 padding 计算

### 8.3 共享设计规则

统一沉淀:

- 大 / 中 / 小 / 胶囊圆角
- page / grouped / elevated / floating 四级表面
- large title / section title / row title / secondary text 四级排版
- 搜索框、列表行、底栏、mini player 的固定高度语义
- 分隔线、边框、轻阴影和半透明强度

## 9. 页面级设计

### 9.1 Home

`Home` 改为 **编辑流优先** 的 Apple Music 式首页。

结构:

1. large title `Home`
2. hero 推荐卡
3. grouped sections
4. 横滑内容区、少量 inset list、少量网格卡片混排

设计要求:

- 首屏主叙事是“浏览内容”，不是“操作入口”
- 当前推荐、歌单、榜单能力保留，但重新编排成编辑流
- 页面滚动时，大标题收缩为 compact nav bar
- 不再保留明显的工具型分段结构

### 9.2 Search

`Search` 改为 **系统搜索页**。

默认态:

- large title `Search`
- 独立搜索框
- 最近搜索 / 建议搜索 / 热门浏览分组

输入后:

- 搜索框吸顶
- 结果按 `Songs / Albums / Artists / Playlists` 分组
- 保持现有搜索能力与分页逻辑，但视觉上更像 iOS 搜索体验

### 9.3 Library

`Library` 改为 **系统式资料库**。

结构:

- large title `Library`
- 顶部先展示分类入口组
- 分类项采用 grouped inset rows
- 下半区可附少量“最近播放 / 最近添加”，但只能做辅助层

设计要求:

- 强分类，弱装饰
- 更像 Apple Music `Library`
- 不让 `Library` 退化成第二个 `Home`

### 9.4 More

`More` 改为 **纯设置中心**。

结构:

- large title `Settings` 或 `More`
- 全页采用 grouped settings rows
- 按账号 / 音源 / 播放 / 下载 / 更新 / 关于等分组

设计要求:

- 不再是功能堆放页
- 首页仅承接设置摘要
- 复杂设置项进入二级页

### 9.5 MiniPlayer

`MiniPlayer` 改为 **悬浮胶囊卡片**。

结构:

- 悬浮在 tab bar 上方
- 不贴满屏
- 左封面、中间标题信息、右侧播放控制
- 附一条极细进度反馈

设计要求:

- 与底栏是一体化底部系统的一部分
- 展开全屏播放器时应具备连续过渡感
- 与底栏之间保持明确呼吸感

### 9.6 FullScreenPlayer / Lyrics / VideoPlayer

#### FullScreenPlayer

- 大封面居中偏上
- 轻动态背景
- 细进度条
- 胶囊 / 圆形控制区
- 整体更克制，避免重玻璃、重辉光

#### Lyrics

- 不改歌词链路
- 保持与全屏播放器一致的排版与材质语法
- 当前行高亮、背景层级、底部控制区统一归一

#### VideoPlayer

- 不改播放链路
- 收回当前独立风格，统一到 Apple 视觉体系
- 保持视频可读性与沉浸感，不为统一而牺牲主内容

## 10. 交互与动效规则

### 10.1 全局基调

全站交互基调为:

- 轻
- 稳
- 顺
- 克制

### 10.2 页面与导航动效

- 页面进入 / 返回: 短距离位移 + 淡入淡出
- large title 收缩: 与滚动联动
- 列表点击反馈: 轻按压，不使用强 Material 水波纹
- sheet / dialog / menu: 统一 Cupertino 弹层节奏
- mini player -> full player: 应表现为同一对象连续展开

### 10.3 底部壳层联动

- `AppleTabBar`、`MiniPlayer`、页面内容区必须统一由 `AppleScaffold` 计算 inset
- 页面不再自行手搓底部 padding
- snackbar、sheet、dialog 的底部偏移都应依赖同一套 chrome state

## 11. 明暗主题与品牌偏移规则

### 11.1 Light

- 近白背景
- 浅灰 grouped background
- 细分隔线
- 蓝色为主要强调色

### 11.2 Dark

- 深灰层级，而非纯黑或霓虹玻璃
- 背景深、分组面更亮一层
- 顶部栏 / 底栏 / 弹层使用克制半透明材质

### 11.3 可保留的项目口音

允许保留:

- 很轻的柔和渐变底色
- 少量细腻玻璃感
- 个别推荐 hero / 播放器背景上的轻氛围色

不再保留:

- QQ 绿主导全局
- Material 原生组件默认味道
- 重玻璃 / 重渐变 / 重高光 / 重描边
- 每页自己发明一套背景与卡片风格

## 12. 实施顺序

建议按四波实施:

### Phase 1: Theme + Tokens + Global Chrome

- 重建主题入口
- 建立 Apple tokens
- 落地 `AppleScaffold`
- 改造 tab bar / safe area / page transition / system bars

### Phase 2: Core Pages

- `Home`
- `Search`
- `Library`
- `More`

### Phase 3: Player Stack

- `MiniPlayer`
- `FullScreenPlayer`
- `Lyrics`
- `VideoPlayer`

### Phase 4: Overlay + Polish

- `Dialog`
- `Sheet`
- `Menu`
- 截图回归、明暗主题调优、细节统一

## 13. 风险与注意事项

1. 如果不先做共享 primitives，页面迁移必然风格失控
2. 如果继续直接使用 Material `NavigationBar` / `OutlinedTextField` / `ModalBottomSheet`，高复刻目标无法成立
3. 如果保留过多旧玻璃和 QQ 绿，最终结果会变成“Apple 骨架 + 旧皮肤残影”
4. 如果页面继续自行计算底部 inset，mini player / tab bar / sheet 很容易互顶
5. `Home`、`Player`、`Search` 的改造都容易越界到业务层，实施时必须严格限制在 UI 容器、视觉组织和交互表达

## 14. 验收标准

### 14.1 视觉验收

- 第一眼明显接近 Apple Music / iOS 系统应用
- `Home / Search / Library / More / Player` 使用同一种设计语言
- 不再明显残留 QQ / Material 默认气质

### 14.2 交互验收

- 大标题收缩、底栏切换、sheet/dialog 展开、mini player 展开逻辑统一
- safe area、状态栏、导航栏、横竖屏下容器留白稳定

### 14.3 工程验收

- 页面改造建立在共享 primitives 之上
- 不依赖页面内硬编码复制
- 实现后通过 `:app:compileDebugKotlin`
- 若新增共享 UI 抽象较多，应补关键 Compose/UI 测试或截图测试

## 15. 不做事项

本轮明确不做:

- 重写导航框架
- 修改业务能力边界
- 修改播放 / 搜索 / 下载 / 收藏的数据来源与领域语义
- 以逐像素复刻 `CyreneMusic` 作为目标
- 通过大量页面内 `if/else` 套皮维持双体系
