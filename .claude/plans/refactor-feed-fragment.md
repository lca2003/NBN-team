# FeedFragment 拆分重构计划

## 问题

`FeedFragment.java` 714 行，承担了太多职责：
- 视图绑定与生命周期
- 频道 Tab 切换
- 分页逻辑（刷新 / 加载更多）
- 曝光检测（可见比例计算、定时调度、上报）
- 交互处理（点赞动画、收藏、分享、点击）
- 筛选标签栏管理
- 状态切换（loading/empty/error）

## 重构策略

保持现有外部接口不变（`FeedInteractionListener`、`AdCatalog`、`InteractionStore`、`FeedAdapter` 等不改），把 Fragment 内部逻辑按职责抽成独立的委托类（Delegate），Fragment 变成薄协调层。

不引入 ViewModel/LiveData（项目当前是纯 Java + 无 Jetpack Lifecycle 架构），只做类级别的提取，保持项目风格一致。

## 拆分方案

### 1. `FeedExposureDelegate` — 曝光检测委托

从 FeedFragment 中提取：
- `checkVisibleExposures()`
- `visibleRatioOf(View)`
- `recordExposure(AdItem, int, float)`
- `scheduleNextExposureCheck(long)`
- `exposureCheckRunnable` 相关逻辑
- `onPause` / `onDestroyView` 中的曝光清理

输入依赖：`RecyclerView`、`LinearLayoutManager`、`FeedAdapter`、`ExposureTracker`、`InteractionStore`、`AdCatalog`、`AnalyticsTracker`

### 2. `FeedInteractionDelegate` — 交互处理委托

从 FeedFragment 中提取：
- `onCardClick()`
- `onLikeClick()` + `playLikeBurst()`
- `onCollectClick()`
- `onShareClick()`
- `onVideoPlayClick()`
- `hydratePersistentCounts()`

输入依赖：`Context`、`RecyclerView`、`FeedAdapter`、`InteractionStore`、`AdCatalog`、`AnalyticsTracker`

### 3. `FeedTagFilterDelegate` — 标签筛选委托

从 FeedFragment 中提取：
- `onTagClick()`
- `renderFilterBar()`
- `removeTagFilter()`
- `clearAllTagFilters()`
- `currentTagFilters` 状态
- `currentSearchAdIds` 状态

输入依赖：`Context`、`LinearLayout filterBar`、`View filterBarContainer`、`View filterClearAll`、`FeedAdapter`

回调：筛选变更时通知 Fragment 刷新数据

### 4. `FeedFragment` 精简后保留

- 视图绑定 (`bindViews`)
- RecyclerView / SwipeRefresh 初始化
- 频道 Tab 切换 (`setupChannelTabs` / `selectTab`)
- 分页协调 (`refreshChannel` / `loadMore`) — 这是 Fragment 的核心骨架
- 状态切换 (`showLoading` / `showEmpty` / `showError` / `hideStatus`)
- 生命周期 (`onResume` / `onPause` / `onDestroyView`) — 薄壳，转发给委托
- `configure()` / `applySearchResult()` 公共入口

## 新增文件

| 文件 | 行数估计 | 职责 |
|------|----------|------|
| `FeedExposureDelegate.java` | ~120 行 | 曝光可见性检测、记录、调度 |
| `FeedInteractionDelegate.java` | ~130 行 | 点赞/收藏/分享/点击处理 + 彩蛋动画 |
| `FeedTagFilterDelegate.java` | ~100 行 | 标签筛选 UI + 状态管理 |

## 重构后 FeedFragment 预估

从 714 行 → ~300 行，只保留视图绑定、分页骨架、状态切换、以及对三个委托的简单转发。

## 不修改的文件（成员 B/C 代码）

- `AdRepository` 及其实现
- `AdItem` / `DataResult` / `PageRequest` / `PageResult` 等数据模型
- `AnalyticsTracker` / `ExposureTracker`
- `InteractionState` / `InteractionAction`

## 不修改的同包文件

- `FeedAdapter.java`
- `AdCatalog.java`
- `FeedPage.java` / `FooterState.java`
- `FeedInteractionListener.java`
- `InteractionStore.java`
- `TagFilter.java` / `TagChipBinder.java`

## 实施步骤

1. 创建 `FeedExposureDelegate.java`
2. 创建 `FeedInteractionDelegate.java`
3. 创建 `FeedTagFilterDelegate.java`
4. 重写 `FeedFragment.java`，引用三个委托，移除已提取的代码
5. 验证编译通过
