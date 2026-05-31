# 成员A 工作文档 —— 信息流 UI 与交互

> 课题：AI 广告推荐信息流（实现单列广告信息流 App）
> 角色：人员A —— 信息流 UI 与交互
> 端侧：Android（Java 21）
> 本文档替代原空白的 `工作文档.txt`，对齐《训练营课题：AI广告推荐信息流.pdf》与 `docs/` 下的团队文档。

## 一、我的职责（来自课题“团队分工参考 · 人员A”）

| 序号 | 职责 | 完成情况 |
| --- | --- | --- |
| 1 | 单列信息流列表布局与滚动优化 | ✅ |
| 2 | 广告卡片多样式（大图/小图/视频，至少 3 种） | ✅ 3 种 |
| 3 | 顶部 Tab 切换与联动刷新 | ✅ 精选/电商/本地 |
| 4 | 下拉刷新、上拉加载（含加载中/空态/错误态） | ✅ 三态齐全 |
| 5 | 广告详情页 UI 与返回位置保持 | ✅ |
| 6 | 点赞/收藏/分享交互 UI 与动画 | ✅ 含点赞彩蛋 |

主要负责包：`ui.feed`、`ui.detail`。

## 二、协作边界（重要）

本轮严格遵守“只做 UI 层、不修改队友文件”的约定：

- **不改** 人员B 的数据层（`data.*`：`AdItem`、`AdRepository`、`MockAdRepository`、`InteractionState`）。
- **不改** 人员C 的搜索/统计/视频（`ui.search.*`、`analytics.*`、`video.*`）。
- 只通过 **接口/公开方法** 调用它们：`AdRepository`（取数据）、`AnalyticsTracker`（上报曝光/点击）、`SearchBottomSheetDialogFragment`（搜索入口）。

## 三、关键技术决策与原因（对应课题“技术方案设计”评分维度）

### 1. 列表与多样式卡片
- 用 `RecyclerView` + 多 `viewType`（大图/小图/视频）实现单列信息流，天然支持 **cell 复用**（课题“资源复用”要求）。
- 三种卡片布局的控件 id 统一命名，复用同一个 `AdViewHolder`，差异仅视频卡多出播放按钮；减少重复代码。
- 标签 chip 用 `TextView` 自绘（`TagChipBinder`），**不引入 Material Chip**，因为当前主题是 `Theme.AppCompat`，引入 Chip 需改动 B/C 共用的 `styles.xml`，风险大。

### 2. 分页与上拉加载（不碰数据层的折中）
- 现状：B 的 `MockAdRepository` 只有 3 条种子数据、无分页接口。
- 方案对比：
  - 方案A：直接改 B 的 Repository 加分页 —— 越界，违反协作约定。
  - 方案B（采用）：在 `ui.feed` 加一个分页垫片 `AdCatalog`，**只通过 `AdRepository` 接口读种子数据**，在客户端循环合成多页 + 模拟网络延迟/失败。
- 收益：上拉加载、加载中/空态/错误态都能完整演示；等 B 提供真实分页接口后，只需替换 `AdCatalog.loadPage` 内部实现，UI 层零改动。

### 3. 跨页状态同步（详情页 ↔ 信息流）
- 课题明确要求：详情页和信息流的点赞/收藏状态要同步。
- 方案：在 `ui.feed` 建一个进程内单一数据源 `InteractionStore`，按 `adId` 统一保存 `InteractionState`（复用 B 的模型，不复制孤立状态）。
- Feed 卡片与详情页都读写 Store 里同一份状态：在详情页点赞，返回列表 `onResume` 刷新即同步。

### 4. 列表位置保持
- 详情页用独立 `Activity`，信息流 `Fragment` 留在返回栈中，`RecyclerView` 的滚动位置由系统自动保留。
- 返回时只在 `FeedFragment.onResume` 刷新可见项的互动状态，不重建列表，因此**位置不丢**。

### 5. 动画
- 页面切换：进入详情 `slide_in_left`，返回 `slide_out_right`（`overridePendingTransition`）。
- 点赞彩蛋：心形图标 `OvershootInterpolator` 放大回弹一次，仅在“点亮”时触发。

### 6. 视频卡（占位说明）
- 课题要求“外流视频默认暂停，点击播放”。当前 `AdItem` 无 `videoUrl`（属 B 数据层），故视频卡做**状态化占位**：默认显示遮罩 + 播放按钮（暂停态），点击切换播放态文案。
- 真实播放器接入点预留：对接 C 的 `VideoPlaybackManager`（同一时刻仅一个活跃视频），等 B 补充 `videoUrl` 后接入。

## 四、交付文件清单（本轮新增/修改）

新增 Java（`app/src/main/java/com/nbn/adfeed/`）：
- `ui/feed/InteractionStore.java` —— 互动状态单一数据源（跨页同步）
- `ui/feed/AdCatalog.java`、`FeedPage.java` —— UI 层分页垫片
- `ui/feed/FeedFragment.java` —— 信息流主页面（Tab/刷新/加载/状态/交互中枢）
- `ui/feed/FeedAdapter.java` —— 多样式卡片 + footer 适配器
- `ui/feed/FeedInteractionListener.java`、`FooterState.java`、`TagChipBinder.java`
- `ui/detail/AdDetailActivity.java` —— 广告详情页

新增布局（`app/src/main/res/layout/`）：
- `fragment_feed.xml`、`item_ad_large_image.xml`、`item_ad_small_image.xml`、`item_ad_video.xml`
- `item_feed_footer.xml`、`view_interaction_bar.xml`、`activity_ad_detail.xml`

新增 drawable / values：点赞/收藏/分享/播放/返回图标、卡片与占位背景、标签 chip 背景；`colors.xml`、`strings.xml` 增补 Feed/详情文案与配色。

修改（UI 范畴）：
- `MainActivity.java` —— 改为承载 `FeedFragment`，保留搜索入口
- `activity_main.xml` —— 改为 Fragment 容器
- `AndroidManifest.xml` —— 注册 `AdDetailActivity`
- `app/build.gradle` —— 新增 `swiperefreshlayout` 依赖

## 五、构建与运行

```bash
# 在项目根目录
./gradlew.bat assembleDebug      # Windows
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

本轮已验证：`assembleDebug` **BUILD SUCCESSFUL**，APK 正常生成。

## 六、自测要点（演示路径）

1. 启动 App → 看到“精选”频道单列信息流，含大图/小图/视频三种卡片。
2. 下拉 → 刷新转圈 → 列表回到顶部。
3. 持续上滑 → 底部出现“加载中…” → 追加新卡片 → 到底显示“没有更多了”。
4. 切换“电商/本地”Tab → 列表联动刷新为该频道数据；某频道无数据时显示空态。
5. 点击卡片 → 滑入详情页 → 点赞（心形变红+彩蛋）→ 返回 → 信息流该卡片点赞态已同步。
6. 点收藏/分享 → 收藏变金色 / 弹出系统分享面板。
7. 视频卡 → 详情页点击播放按钮 → 状态在“已暂停/播放中”间切换。

## 七、已知限制与后续

- 图片/视频为占位视觉：待 B 在 `AdItem` 补 `imageUrl/videoUrl` 后，由 B 接入图片加载库、C 接入真实播放器。
- 分页为客户端模拟：待 B 提供真实分页接口，替换 `AdCatalog.loadPage` 内部即可。
- 曝光统计：目前在“进入详情计点击”已接入 `AnalyticsTracker`；按课题“可见面积>50% 且停留≥1s 计一次曝光”的曝光口径，建议与 C 对齐后在 Feed 滚动监听里补充（属统计口径，归 C 维护）。

## 八、AI 声明

本轮信息流 UI 与交互代码在 AI 辅助下完成。功能理解、协作边界、技术取舍由人工确认；AI 产出已通过 `assembleDebug` 构建验证与上述自测路径核对。关键逻辑均加中文注释说明设计原因。