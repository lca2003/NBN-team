# B0 基线审计

## 构建结果

- 基线命令：`./gradlew testDebugUnitTest assembleDebug > /tmp/nbn-b0-baseline.log 2>&1`
- 基线结果：`BUILD SUCCESSFUL`
- JDK：按仓库要求使用 `/usr/libexec/java_home -v 21`
- 当前阶段测试：`./gradlew testDebugUnitTest > /tmp/nbn-b1-b6-tests.log 2>&1` 已通过

## Git 状态摘要

基线进入实现前仅发现未跟踪协作文档：

```text
?? docs/b0-execution-plan.md
?? docs/daily/
?? docs/team-execution-plan.md
```

执行原则：这些文件视为既有协作内容，不删除、不整目录重写。

## data.model

- 当前状态：已补齐 `AdItem`、`AdStats`、`InteractionState`、`PageRequest`、`PageResult<T>`、`DataResult<T>`、`SearchRequest`、`InteractionAction`。
- 已覆盖：Feed、详情、搜索、统计、AI 缓存需要的字段。
- 缺口：真实 Remote 接口字段仍需后端稳定后对齐。

## data.repository

- 当前状态：`AdRepository` 已冻结 v1 四个方法。
- 已新增：`DefaultAdRepository` 支持 Remote 失败 fallback 到 Mock。
- 缺口：Remote 当前为可替换占位，不连接真实服务。

## data.mock

- 当前状态：`MockAdRepository` 是单一 Mock 状态源。
- 已新增：`app/src/main/assets/ads_mock.json`，并提供 JSON 解析测试。
- 缺口：图片和视频 URL 使用演示占位地址。

## data.remote

- 当前状态：`RemoteAdDataSource` 已按 v1 模型定义。
- 已新增：`FailingRemoteAdDataSource` 用于失败注入和 fallback 验证。
- 缺口：无真实网络实现。

## ai

- 当前状态：已提供 `AiResponse<T>`、摘要服务、标签服务、缓存和规则/Mock 降级。
- 已覆盖：摘要长度、标签数量、缓存命中、内容变更缓存失效、Remote 失败降级。
- 缺口：真实远程 AI 未接入。

## analytics

- 当前状态：已提供 `AnalyticsEvent`、`AnalyticsEventType` 和 `AnalyticsTracker`。
- 已覆盖：曝光、点击、点赞、收藏、分享、搜索。
- 缺口：当前为内存统计，未持久化。

## UI 对 B 的依赖

- `ui.feed`：依赖 `loadAds`、`updateInteraction`、`AdItem` 字段和 `DataResult` 空/错态。
- `ui.detail`：依赖 `getAdById`、`updateInteraction`、AI 摘要/标签。
- `ui.search`：依赖 `searchAds` 和 Repository 返回的广告状态。

## 保留 / 改造 / 新增

- 保留：单 App 模块、Java 21、`AdContentType`、包分层方向。
- 改造：旧 `AdRepository`、旧 `MockAdRepository`、旧 AI 返回结构、MainActivity 静态首页。
- 新增：Repository v1、Mock JSON、AI 缓存降级、统计事件、Feed/详情/搜索最小闭环、单元测试。

## A 需要 B 提供

- Feed 卡片字段：标题、品牌、频道、摘要、图片、视频、标签、统计、互动状态。
- 详情字段：描述、图片、视频、统计、AI 摘要、AI 标签。
- 接口：分页加载、详情查询、互动更新。
- 状态：点赞、收藏、点击、曝光、分享由 Repository 返回结果为准。

## C 需要 B 提供

- 搜索字段：标题、品牌、描述、标签、频道。
- 统计字段：`eventType`、`adId`、`channelId`、`timestamp`、`sourcePage`。
- 接口：搜索分页、标签过滤、可关联广告 ID。
- 状态：搜索结果与 Feed/详情同源。

## 风险清单

- P0：无。当前测试通过，Repository v1 已冻结，UI 不直接读取 Mock JSON。
- P1：真实 Remote 和真实 AI 未接入，答辩时需说明当前为可替换边界和失败注入。
- P2：UI 是基础原生控件样式，统计为内存面板，图片/视频未做真实加载播放。

## 是否允许进入 B1/B2

允许。B1/B2 已完成并通过单元测试，后续重点是继续完善 UI 体验和真实服务适配。
