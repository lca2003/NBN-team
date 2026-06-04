# 真正前后端分离执行计划

日期：2026-05-31
目标：把当前 `Android App + Mock/Demo Remote` 升级为 `Android App + 独立后端服务 + 真实 HTTP 数据接入`。
边界：本文件只制定执行计划，不改业务实现。实施时必须继续遵守 `AGENTS.md` 的 JDK 21、文件锁、验证和密钥约束。

## 执行进度更新

截至 2026-05-31，本计划已从纯计划推进到可运行实现：

- 已新增独立 Gradle `:backend` Java 21 application 模块。
- 后端可作为独立进程启动，并通过 `/health` 暴露 seed 加载和域计数。
- `backend/src/main/resources/seed/` 已迁入 7 个 seed JSON。
- 已实现首页信息流、详情、互动、用户中心、AI 搜索/摘要/标签/rerank、消息、评论评价、商家购买、配置素材、埋点统计等 `/v1/...` HTTP API。
- Android WebView 主路径已通过 `/v1/stitch/pages/*` 接入后端页面 payload，并保留本地 fallback。
- Android 原生 `AdRepository` 默认优先使用 `BackendRemoteAdDataSource`，失败后 fallback 到 Mock。
- Android 已补齐用户、消息、评论评价、配置素材、埋点统计等逐域后端 client 入口。
- 已实现 JSON state 持久化：主要写接口会写入 state 目录，并可在后端重启后恢复。
- 已实现可配置 Cloud AI provider：`NBN_AI_API_KEY`、`NBN_AI_ENDPOINT`、`NBN_AI_MODEL` 齐备时优先 `remote_ai`，否则 fallback。
- 已修复 `:backend:run` 进程保活问题，后端可持续监听直到进程被终止。
- 当前安装的 Android debug 包已通过真机 `adb reverse` 命中后端 `/v1/stitch/pages/home`。
- 已通过 `:backend:test`、独立后端 `curl` 冒烟、`testDebugUnitTest assembleDebug` 和 `git diff --check`。
- 完成度审计见 `reports/backend-split-completion-audit-2026-05-31.md`。

仍未完成的后续增强：

- 当前持久化为 JSON state 文件，不是数据库；后续可替换为 SQLite/PostgreSQL。
- Cloud AI provider 已具备可配置 HTTP 接入能力；尚未用真实生产密钥做外部模型验收。
- 当前 Stitch UI 主路径主要通过后端页面 payload 获取数据；逐域 native client 已存在，但消息/我的/评论等页面尚未全部重写为原生 Activity/Fragment。

## 制定计划时的基线判断

当前仓库仍是单 Android App：

- `settings.gradle` 只包含 `:app`。
- `RepositoryProvider` 运行时装配 `DemoRemoteAdDataSource + MockAdRepository`。
- `DemoRemoteAdDataSource` 名义上是 Remote，但实际读取 `MockAdRepository`。
- `docs/api-contract.md` 已有 `/v1/...` 契约，但还没有独立 server 进程、HTTP 路由、数据库或部署地址。

因此，当时的下一阶段是新增真实后端服务，并把 Android 的 Remote 数据源从 Demo/Mock 替换为 HTTP client。上方“执行进度更新”记录了当前已经完成的部分。

## 完成定义

达到“真正前后端分离”至少满足以下条件：

- 仓库中存在独立后端工程，例如 `backend/` 或 Gradle `:backend` 模块。
- 后端可作为独立进程启动，监听本机或局域网端口，例如 `http://127.0.0.1:8080`。
- 后端实现 `/v1/...` API，不依赖 Android 进程运行。
- 后端维护服务端状态：用户资料、点赞、收藏、关注、笔记、评论、消息、曝光、点击等写操作必须在服务端生效。
- Android App 通过真实 HTTP 请求读取和写入数据。
- App 仍保留 Mock fallback，但默认演示路径优先走后端。
- `DemoRemoteAdDataSource` 不再作为默认“远程数据”实现，只保留为测试或离线 fallback。
- 真机或模拟器能够访问后端并展示服务端返回的数据。

## 技术路线建议

### 推荐方案

新增 `:backend` Java 21 Gradle application 模块：

- 使用 Java 21，与当前 Android 工程版本一致。
- MVP 阶段可用 JDK 自带 `HttpServer` 或轻量 HTTP 框架。
- 数据先用 JSON seed + 内存 store + 可选 JSON 落盘，降低实现风险。
- 第二阶段再替换为 SQLite/PostgreSQL，不影响 Android API。

### 不建议一开始做的事

- 不先上复杂微服务。
- 不把 AI API Key 写入仓库。
- 不在 Android 端直接读服务端数据库。
- 不删除现有 Mock；Mock 应作为 fallback 和测试 fixtures。

## 目标目录结构

```text
backend/
  build.gradle
  src/main/java/com/nbn/backend/
    BackendServer.java
    http/
      Router.java
      RequestContext.java
      JsonResponse.java
      ErrorCode.java
    domain/
      feed/
      ads/
      interactions/
      ai/
      messages/
      users/
      analytics/
      config/
    store/
      InMemoryStore.java
      JsonSeedLoader.java
      JsonStateWriter.java
  src/main/resources/seed/
    home_feed.json
    ad_details.json
    search_results.json
    messages.json
    profile.json
    reviews.json
    app_config.json
  src/test/java/com/nbn/backend/

app/src/main/java/com/nbn/adfeed/data/remote/
  HttpApiClient.java
  BackendRemoteAdDataSource.java
  BackendStitchDataSource.java
  BackendConfig.java
  ApiErrorMapper.java
```

## 分阶段执行计划

### Phase 0：契约冻结与基线保护

目标：先把要接的 API 和响应结构定死，避免 Android 与后端并行开发时反复返工。

任务：

- 更新 `docs/api-contract.md`，明确统一响应 envelope、分页格式、错误码、用户态 Header。
- 增加 `docs/backend-split-execution-plan.md` 作为实施路线。
- 明确服务端 seed 数据以 `app/src/main/assets/stitch_data/*.json` 当前结构为初始来源。
- 标记 `DemoRemoteAdDataSource` 为 demo/fallback，不再作为“真实 remote”口径。

验收：

- 文档列出所有 `/v1/...` API。
- 每个接口有请求参数、响应字段、错误码、是否需要用户态。
- `git diff --check` 通过。

### Phase 1：后端服务骨架

目标：先让独立后端能启动、能健康检查、能返回 JSON。

任务：

- 新增 Gradle `:backend` 模块。
- 新增 `BackendServer`，默认监听 `8080`。
- 实现 `GET /health`，返回服务版本、启动时间、seed 加载状态。
- 实现统一响应：

```json
{
  "requestId": "req-xxx",
  "code": "OK",
  "message": "ok",
  "data": {}
}
```

- 实现统一错误：

```json
{
  "requestId": "req-xxx",
  "code": "NOT_FOUND",
  "message": "resource not found",
  "data": null
}
```

验收：

- `./gradlew :backend:test` 通过。
- `./gradlew :backend:run` 可启动。
- `curl http://127.0.0.1:8080/health` 返回 OK。

### Phase 2：服务端数据模型与 seed store

目标：把 Stitch UI 六个域变成后端真实数据源。

任务：

- 在 `backend/src/main/resources/seed/` 放置 7 个 seed 文件：
  - `home_feed.json`
  - `ad_details.json`
  - `search_results.json`
  - `messages.json`
  - `profile.json`
  - `reviews.json`
  - `app_config.json`
- 建立服务端 store：
  - `adsById`
  - `detailsByAdId`
  - `channels`
  - `reviewsByAdId`
  - `commentsByTarget`
  - `usersById`
  - `postsByUserId`
  - `followersByUserId`
  - `notificationsByUserId`
  - `conversationsByUserId`
  - `messagesByConversationId`
  - `analyticsEvents`
  - `exposureEvents`
  - `assetManifest`
  - `remoteConfig`
- 建立 demo 用户态，MVP 使用 Header：

```text
X-Demo-User-Id: user_demo_001
```

验收：

- 后端启动时能加载全部 seed。
- 缺字段、重复 ID、无效关联会在测试中失败。
- 服务端不依赖 Android assets 读取数据。

### Phase 3：首页/详情/互动 API

目标：先打通 App 最核心的 Feed、详情、互动闭环。

接口：

```text
GET /v1/feed/channels
GET /v1/feed?channel=featured&cursor=&limit=
GET /v1/ads/{adId}
GET /v1/ads/{adId}/related
POST /v1/ads/{adId}/exposure
GET /v1/ads/{adId}/detail
GET /v1/ads/{adId}/commerce
POST /v1/ads/{adId}/like
DELETE /v1/ads/{adId}/like
POST /v1/ads/{adId}/collect
DELETE /v1/ads/{adId}/collect
POST /v1/ads/{adId}/share
POST /v1/ads/{adId}/click
```

数据要求：

- `AdItem` 包含广告 ID、标题、副标题、描述、封面图、视频地址、广告类型、品牌、分类、发布时间。
- `MediaAsset` 包含 URL、本地 asset 名、宽高、裁切策略、主色、blur hash。
- `CreatorProfile` 包含发布者 ID、昵称、头像、认证状态、简介。
- `AdTag` 覆盖运动、科技感、学生党、本地、餐饮、社交、白领等标签。
- `AdStats` 包含点赞、评论、收藏、分享、曝光、点击。
- `InteractionState` 必须按当前用户返回是否点赞、收藏、关注、已分享。
- `FeedPage` 返回 cursor、items、hasMore。
- `AdDetail` 返回长文案、AI 深度洞察、卖点、素材组、商家、优惠、评价、评论、相关推荐。

验收：

- Feed 首屏来自后端。
- 点击详情来自后端。
- 点赞、收藏、分享、点击会改变服务端 `stats` 和当前用户 `interactionState`。
- 断网或后端关闭时，App 明确 fallback 到本地 Mock，并显示来源。

### Phase 4：Android HTTP 接入

目标：App 真实请求后端，而不是继续走 Demo Remote。

任务：

- 新增 `BackendConfig`：
  - `apiBaseUrl`
  - `connectTimeoutMs`
  - `readTimeoutMs`
  - `retryCount`
  - `useMockFallback`
- MVP 可先用 `HttpURLConnection`，后续再切 OkHttp。
- 新增 `HttpApiClient`：
  - GET/POST/PATCH/DELETE
  - JSON body
  - timeout
  - retry
  - requestId
  - HTTP 状态码映射
- 新增 `BackendRemoteAdDataSource implements RemoteAdDataSource`。
- `RepositoryProvider` 默认选择：
  - 有 `NBN_API_BASE_URL` 或 BuildConfig 配置时走后端。
  - 后端失败且 `useMockFallback=true` 时走 Mock。
  - 单测可注入 fake client。
- App 真机调本机服务时：
  - Android 模拟器使用 `http://10.0.2.2:8080`。
  - 真机使用电脑局域网 IP，例如 `http://192.168.x.x:8080`。
  - `AndroidManifest.xml` 需要允许 cleartext 或改成本地 HTTPS。

验收：

- `RepositoryProvider` 不再默认注入 `DemoRemoteAdDataSource`。
- Feed、详情、搜索至少一条主链路能从后端返回。
- 后端关闭时 fallback 行为可测试。

### Phase 5：AI 搜索与 AI 生成接口

目标：AI 相关接口也走后端，Android 不直接持有模型密钥。

接口：

```text
POST /v1/ai/search
GET /v1/ai/search/suggestions
GET /v1/ai/search/sessions/{sessionId}
POST /v1/ai/search/sessions/{sessionId}/messages
POST /v1/ai/ads/{adId}/summary
POST /v1/ai/ads/{adId}/tags
POST /v1/ai/ads/rerank
```

后端任务：

- 定义 `AiProvider` 接口：
  - `MockAiProvider`
  - `RuleFallbackAiProvider`
  - `CloudAiProvider`
- MVP 默认用 `MockAiProvider + RuleFallbackAiProvider`。
- 云端模型 API Key 只允许通过环境变量读取，例如 `NBN_AI_API_KEY`。
- 记录 AI fallback 原因：
  - `AI_DISABLED`
  - `AI_TIMEOUT`
  - `AI_PROVIDER_ERROR`
  - `AI_RATE_LIMITED`
- `AiSearchSession` 和 `AiSearchMessage` 在服务端保存。

Android 任务：

- `DefaultAdAiService` 改为优先请求后端 `/v1/ai/...`。
- 保留本地 cache 和 fallback。
- UI 显示来源：`remote_ai`、`backend_fallback`、`cache`、`local_fallback`。

验收：

- 搜索建议来自后端。
- AI 搜索会话可查询历史。
- 后端禁用 AI 时，App 有明确 fallback 文案和默认结果。
- 仓库中没有任何 API Key。

### Phase 6：消息、我的、用户 CRUD

目标：补齐用户体系，让“个人资料、收藏、点赞、笔记、关注”真正服务端化。

接口：

```text
GET /v1/notifications/summary
GET /v1/notifications?type=like&cursor=&limit=
POST /v1/notifications/read
POST /v1/notifications/read-all
GET /v1/conversations?cursor=&limit=
GET /v1/conversations/{conversationId}/messages?cursor=&limit=
POST /v1/conversations/{conversationId}/messages
GET /v1/ai-assistant/digest
GET /v1/users/me
PATCH /v1/users/me
GET /v1/users/me/stats
GET /v1/users/me/achievements
GET /v1/users/{userId}/posts?tab=notes
GET /v1/users/{userId}/posts?tab=collections
GET /v1/users/{userId}/posts?tab=liked
GET /v1/users/{userId}/followers
GET /v1/users/{userId}/following
POST /v1/users/{userId}/follow
DELETE /v1/users/{userId}/follow
```

新增或强化接口：

```text
POST /v1/users
POST /v1/users/me/posts
PATCH /v1/users/me/posts/{postId}
DELETE /v1/users/me/posts/{postId}
```

数据要求：

- `UserProfile` 包含用户 ID、昵称、头像、等级、简介和个人信息。
- `ProfileStats` 包含获赞与收藏、关注数、粉丝数、发布数。
- `Achievement` 包含连续签到、获赞破万、创作达人、新动态等。
- `ProfilePost` 支持我的笔记、收藏、赞过内容。
- `FollowRelation` 必须按 `followerId + followingId` 保存。
- 收藏/点赞必须是用户级记录，不能只改广告计数。

验收：

- 修改昵称/简介后刷新 App 仍保留。
- 发布笔记后 `tab=notes` 能看到新笔记。
- 收藏广告后 `tab=collections` 能看到对应内容。
- 点赞广告后 `tab=liked` 能看到对应内容。
- 关注/取关会影响 followers/following 计数和关系列表。

### Phase 7：评论/评价、商家/购买、配置/埋点

目标：补齐剩余业务域和统计闭环。

接口：

```text
GET /v1/ads/{adId}/reviews?cursor=&limit=
POST /v1/ads/{adId}/reviews
POST /v1/reviews/{reviewId}/like
GET /v1/comments?targetType=ad&targetId={adId}
POST /v1/comments
GET /v1/merchants/{merchantId}
GET /v1/merchants/{merchantId}/nearby
POST /v1/orders/checkout-intent
GET /v1/config/app
GET /v1/assets/manifest
GET /v1/design-content/home
POST /v1/events/batch
POST /v1/exposures/batch
GET /v1/analytics/summary
```

数据要求：

- `Review` 包含评价 ID、用户头像、昵称、时间、内容、点赞数。
- `Comment` 支持目标类型、目标 ID、回复关系、内容、时间。
- `Merchant` 包含商家名、头像、评分、地址、距离、营业状态。
- `Offer` 包含价格、原价、优惠文案、CTA 文案。
- `AnalyticsEvent` 支持曝光、点击、点赞、收藏、分享、搜索、详情打开。
- `ExposureEvent` 支持广告 ID、位置、停留时间、是否有效曝光。
- `RemoteConfig` 支持功能开关、频道配置、AI 是否开启。
- `AssetManifest` 支持 Stitch 图片素材映射、本地 asset 与远程 URL 对应关系。

验收：

- 评论和评价写入后能分页读取。
- 埋点批量上报后 `/v1/analytics/summary` 能聚合统计。
- RemoteConfig 能控制 AI 开关和频道开关。
- AssetManifest 能让 App 使用服务端素材 URL。

### Phase 8：Stitch WebView 数据切换

目标：让 Stitch UI 从 Android assets 注入转为后端数据注入。

任务：

- `StitchDataRepository` 增加 remote 实现，例如 `BackendStitchDataSource`。
- WebView 注入数据来源改为：
  - 优先后端。
  - 失败时 fallback 到 `app/src/main/assets/stitch_data`。
- `window.NBN_STITCH_DATA` 继续作为 WebView 页面唯一数据入口。
- `StitchDataRendererScript` 继续消费同一结构，避免重写 HTML。

验收：

- 修改后端 seed 中的 profile 昵称，重启后端并刷新 App，`我的`页能显示新昵称。
- 修改后端 feed 标题，首页能显示新标题。
- 关闭后端，App fallback 到本地数据并有日志或 UI 来源标识。

## API 优先级矩阵

| 优先级 | 域 | API | 原因 |
| --- | --- | --- | --- |
| P0 | 健康检查 | `GET /health` | 证明后端独立运行 |
| P0 | 首页 | `GET /v1/feed/channels`、`GET /v1/feed` | App 首屏必须真实接后端 |
| P0 | 详情 | `GET /v1/ads/{adId}`、`GET /v1/ads/{adId}/detail` | 点击链路必须可用 |
| P0 | 互动 | like、collect、share、click、exposure | 证明服务端状态可写 |
| P0 | 我的 | `GET /v1/users/me`、`PATCH /v1/users/me` | 证明用户资料可编辑 |
| P0 | 配置 | `GET /v1/config/app` | 控制后端/AI/Mock 开关 |
| P1 | AI 搜索 | search、suggestions、messages、summary、tags | 保留 AI 特色 |
| P1 | 用户内容 | posts、collections、liked、follow | 补齐用户体系 |
| P1 | 消息 | notifications、conversations、messages | 补齐消息页 |
| P2 | 评论评价 | reviews、comments | 增强详情真实感 |
| P2 | 商家购买 | merchant、nearby、checkout-intent | 增强商业闭环 |
| P2 | 埋点聚合 | events、exposures、analytics summary | 增强统计闭环 |

## Android 验证矩阵

| 场景 | 期望 |
| --- | --- |
| 后端启动，App 打开首页 | Feed 数据来自 `/v1/feed` |
| 后端修改 seed 标题后重启 | App 刷新显示新标题 |
| 点击详情 | 数据来自 `/v1/ads/{adId}/detail` |
| 点赞广告 | 服务端 stats +1，当前用户 liked=true |
| 收藏广告 | 我的收藏列表出现该广告 |
| 编辑资料 | 服务端保存，重启 App 后仍保留 |
| 发布笔记 | 我的笔记列表出现新笔记 |
| AI 关闭 | App 显示 fallback 文案和默认结果 |
| 后端关闭 | App fallback 到本地 Mock，并可区分数据来源 |
| 弱网超时 | App 不崩溃，显示错误或 fallback |

## 验证命令计划

后端：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :backend:test
./gradlew :backend:run
curl http://127.0.0.1:8080/health
curl "http://127.0.0.1:8080/v1/feed?channel=featured&limit=3"
```

Android：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

集成：

```bash
curl -X POST http://127.0.0.1:8080/v1/ads/{adId}/like -H "X-Demo-User-Id: user_demo_001"
curl http://127.0.0.1:8080/v1/users/user_demo_001/posts?tab=liked
```

## 实施顺序建议

1. 先做 `:backend` 骨架、`/health`、统一响应、seed 加载。
2. 再做 Feed/详情/互动 P0 API。
3. 再做 Android `HttpApiClient` 和 `BackendRemoteAdDataSource`。
4. 再把 `RepositoryProvider` 默认路径切到后端，Mock 只做 fallback。
5. 再做用户资料编辑、收藏/点赞列表、笔记 CRUD。
6. 再做 AI 搜索、消息、评论、商家购买、埋点聚合。
7. 最后做真机联调证据：后端启动日志、curl 证据、App 截图/UI dump、全量构建测试。

## 风险与控制

- 风险：真机访问本机后端失败。控制：优先模拟器 `10.0.2.2`，真机使用局域网 IP，并记录防火墙/同网段检查。
- 风险：一次性实现全部接口导致不可验证。控制：P0 先闭环首页、详情、互动、我的。
- 风险：服务端状态与 App Mock 不一致。控制：后端 seed 成为主真相源，App assets 只做 fallback。
- 风险：AI Key 泄露。控制：只读环境变量，不提交任何密钥。
- 风险：后端写操作无持久化。控制：MVP 至少 JSON state 落盘，下一阶段再迁移数据库。
- 风险：现有脏工作区冲突。控制：每阶段先登记 file-lock，只改本阶段文件，运行 `git diff --check`。

## 下一步可执行任务包

### Task A：后端骨架

范围：

- `settings.gradle`
- `backend/build.gradle`
- `backend/src/main/java/com/nbn/backend/BackendServer.java`
- `backend/src/test/java/com/nbn/backend/BackendServerTest.java`
- `docs/api-contract.md`

完成条件：

- `:backend` 能启动。
- `/health` 可访问。
- `:backend:test` 通过。

### Task B：Feed/详情/互动 API

范围：

- `backend/src/main/resources/seed/*.json`
- `backend/src/main/java/com/nbn/backend/domain/feed/*`
- `backend/src/main/java/com/nbn/backend/domain/ads/*`
- `backend/src/main/java/com/nbn/backend/domain/interactions/*`

完成条件：

- Feed 和详情可被 curl 访问。
- 点赞/收藏/曝光/点击能改变服务端状态。

### Task C：Android 真实 HTTP 接入

范围：

- `app/src/main/java/com/nbn/adfeed/data/remote/HttpApiClient.java`
- `app/src/main/java/com/nbn/adfeed/data/remote/BackendRemoteAdDataSource.java`
- `app/src/main/java/com/nbn/adfeed/data/repository/RepositoryProvider.java`
- 网络配置和单测。

完成条件：

- App 首屏 Feed 来自后端。
- 后端失败时 fallback 到 Mock。
- `testDebugUnitTest assembleDebug` 通过。

### Task D：用户体系 CRUD

范围：

- 后端 users/posts/follow routes。
- Android user/profile repository。
- Stitch WebView profile 数据刷新。

完成条件：

- 用户资料可编辑。
- 收藏/点赞列表是服务端用户级数据。
- 笔记可发布、编辑、删除。

## 当前结论

这条路线完成后，项目才能从“Android App + Mock/Demo Remote”升级为“Android App + 独立后端服务 + 真实 HTTP 数据接入”。第一阶段不应再继续强化 DemoRemote，而应先新增 `:backend` 和 `/health`，再按 P0 API 把 Feed、详情、互动和我的页切到真实 HTTP。
