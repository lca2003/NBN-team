# 接口契约

## Repository v1

UI、搜索、统计只能通过 `AdRepository` 获取和更新广告数据，不直接读取 Mock JSON。

```java
DataResult<PageResult<AdItem>> loadAds(PageRequest request);
DataResult<AdItem> getAdById(String adId);
DataResult<PageResult<AdItem>> searchAds(SearchRequest request);
DataResult<AdItem> updateInteraction(String adId, InteractionAction action);
```

## 数据结果

`DataResult<T>` 统一表达成功、空态、错误和降级。

```text
SUCCESS
EMPTY
TIMEOUT
PARSE_ERROR
REMOTE_ERROR
FALLBACK
```

字段：

- `status`：结果状态。
- `data`：成功、空分页或 fallback 数据。
- `source`：`mock`、`remote` 等数据来源。
- `message`：可展示或记录的错误说明。
- `error`：异常对象，UI 不直接依赖。

## 后端状态持久化

独立后端以 `backend/src/main/resources/seed/*.json` 作为初始数据源，写操作会落盘到 JSON state 目录。

- 启动入口：`BackendServer.main` 默认使用 `build/nbn-backend-state`。
- 覆盖目录：设置 `NBN_BACKEND_STATE_DIR=/path/to/state` 或 JVM 参数 `-Dnbn.backend.stateDir=/path/to/state`。
- 读取顺序：若 state 目录存在对应 JSON 文件，则优先读取 state；否则读取 seed。
- 写入范围：`home_feed.json`、`profile.json`、`reviews.json`、`messages.json`、`search_results.json`、`app_config.json`。
- 当前仍不是数据库；它满足 MVP 的跨进程重启恢复，后续可迁移到 SQLite/PostgreSQL。

`GET /health` 的 `data.state` 返回：

```json
{
  "persistenceEnabled": true,
  "directory": "/absolute/path/to/state"
}
```

## 分页请求

```json
{
  "channel": "精选",
  "cursor": "page_1",
  "pageSize": 10,
  "refresh": true,
  "sourcePage": "feed"
}
```

规则：

- `page_1` 表示第一页。
- `nextCursor == null` 表示无更多。
- 越界页返回 `DataResult.EMPTY`，`PageResult.items` 为空。
- 刷新必须重新请求第一页，UI 不追加旧数据。

## 分页响应

```json
{
  "items": [],
  "currentCursor": "page_1",
  "nextCursor": "page_2",
  "hasMore": true,
  "pageNumber": 1,
  "pageSize": 10,
  "totalCount": 30,
  "dataSource": "mock"
}
```

## 广告模型

```json
{
  "id": "ad_001",
  "title": "轻量跑鞋新品首发",
  "brand": "NBN Sports",
  "channel": "精选",
  "channelId": "精选",
  "description": "主打轻量缓震和夜跑反光设计，适合校园通勤、夜跑和日常训练。",
  "summary": "轻量缓震跑鞋，适合通勤夜跑。",
  "imageUrl": "stitch_ui/images/stitch-02.png",
  "thumbnailUrl": "stitch_ui/images/stitch-02.png",
  "videoUrl": null,
  "contentType": "LARGE_IMAGE",
  "tags": ["运动", "学生党", "性价比", "通勤"],
  "interaction": {
    "liked": false,
    "collected": false
  },
  "stats": {
    "exposureCount": 1680,
    "clickCount": 268,
    "likeCount": 320,
    "collectCount": 180,
    "shareCount": 46
  },
  "contentHash": "mock_001"
}
```

## 搜索请求

```json
{
  "query": "学生党运动",
  "channel": "",
  "tag": "",
  "cursor": "page_1",
  "pageSize": 10,
  "sourcePage": "search"
}
```

搜索匹配字段：

- `title`
- `brand`
- `channel`
- `description`
- `summary`
- `tags`

固定演示关键词：`学生党`、`运动`、`咖啡`、`数码`、`通勤`。

## 互动动作

```text
LIKE
UNLIKE
TOGGLE_LIKE
COLLECT
UNCOLLECT
TOGGLE_COLLECT
CLICK
EXPOSE
SHARE
```

规则：

- Repository 是 Feed、详情、搜索的单一状态源。
- 点赞、收藏返回更新后的 `AdItem`。
- 点击、曝光、分享更新 `AdStats`。
- 不存在 ID 返回 `DataResult.EMPTY`。

## AI 接口

```java
AiResponse<String> getAiSummary(String adId);
AiResponse<List<String>> getAiTags(String adId);
```

`AiResponse<T>` 字段：

- `value`：摘要或标签。
- `source`：`remote_ai`、`cache`、`rule_fallback`、`mock_fallback`。
- `cached`：是否命中缓存。
- `message`：降级说明。
- `error`：异常对象。

Android 实现规则：

- `RepositoryProvider.getAdAiService(Context)` 默认使用 `BackendAdAiService`。
- `BackendAdAiService` 优先请求：
  - `POST /v1/ai/ads/{adId}/summary`
  - `POST /v1/ai/ads/{adId}/tags`
- 后端不可用、响应异常或返回空内容时，Android fallback 到本地 `DefaultAdAiService`。
- 无 `Context` 的测试路径仍可使用本地 `DefaultAdAiService`，避免单测依赖网络。

约束：

- 摘要不超过 40 个中文字符。
- 标签 3-5 个。
- 单个标签不超过 6 个中文字符。
- 缓存 Key 为 `adId + contentHash + promptVersion`。

## 统计事件

```json
{
  "eventType": "AD_CLICK",
  "adId": "ad_001",
  "channelId": "精选",
  "timestamp": 1760000000000,
  "sourcePage": "feed",
  "keyword": ""
}
```

事件类型：

- `APP_OPEN`
- `AD_EXPOSURE`
- `AD_CLICK`
- `AD_LIKE`
- `AD_COLLECT`
- `AD_SHARE`
- `SEARCH`

统计失败不能影响 Repository 互动状态。

## Stitch UI Data Contract v1

当前 Stitch WebView skin 需要把静态页面逐步改成数据驱动。数据先按 6 个域拆分：

- 首页信息流：`app/src/main/assets/stitch_data/home_feed.json`
- 详情页：`app/src/main/assets/stitch_data/ad_details.json`
- AI 搜索：`app/src/main/assets/stitch_data/search_results.json`
- 消息页：`app/src/main/assets/stitch_data/messages.json`
- 我的页：`app/src/main/assets/stitch_data/profile.json`
- 评论/配置/统计：`reviews.json`、`app_config.json`

Java 契约先落在 `com.nbn.adfeed.data.model.stitch`，不破坏现有 `AdRepository`：

- `StitchFeedModels`：`MediaAsset`、`CreatorProfile`、`AdTag`、`StitchAdStats`、`StitchInteractionState`、`FeedChannel`、`FeedAdItem`、`FeedPage`。
- `StitchDetailModels`：`AdDetail`、`Merchant`、`Offer`、`Review`、`Comment`、`RelatedItem`。
- `StitchSearchModels`：`AiSearchSession`、`AiSearchMessage`、`AiSearchResult`、`AiRecommendationReason`、`SearchSuggestion`、`AiFallback`。
- `StitchMessageModels`：`NotificationSummary`、`NotificationItem`、`Conversation`、`Message`、`AiAssistantDigest`。
- `StitchProfileModels`：`UserProfile`、`ProfileStats`、`Achievement`、`ProfilePost`、`FollowRelation`。
- `StitchConfigModels`：`AppRemoteConfig`、`FeatureSwitch`、`ChannelConfig`、`StitchAnalyticsEvent`、`ExposureEvent`、`AssetManifest`、`AssetEntry`。

### 首页信息流接口

```text
GET /v1/feed/channels
GET /v1/feed?channel=featured&cursor=&limit=
GET /v1/ads/{adId}
GET /v1/ads/{adId}/related
POST /v1/ads/{adId}/exposure
```

`FeedAdItem` 必须包含：

- `adId`、`title`、`subtitle`、`description`
- `cover`、`video`
- `adType`、`brand`、`category`、`publishTime`
- `creator`、`tags`、`stats`、`interactionState`
- `likedUserIds`、`collectedUserIds`、`sharedUserIds` 作为后端关系记录；前端只展示 `stats` 和 `interactionState`，不能反推关系。
- 广告 `creator.userId` 固定为系统管理员账号 `1`，非广告帖子不得使用系统管理员账号冒充真实用户。

### 详情页接口

```text
GET /v1/ads/{adId}/detail
GET /v1/ads/{adId}/commerce
GET /v1/merchants/{merchantId}
GET /v1/merchants/{merchantId}/nearby
POST /v1/orders/checkout-intent
```

`AdDetail` 必须包含：

- `title`、`longCopy`、`aiDeepInsight`
- `sellingPoints`、`mediaAssets`
- `merchant`、`offer`
- `reviews`、`comments`、`relatedItems`

后端实现状态：

- `GET /v1/ads/{adId}/commerce` 从详情 seed 返回 `merchant`、`offer` 和 `sellingPoints`。
- `GET /v1/merchants/{merchantId}` 与 `GET /v1/merchants/{merchantId}/nearby` 使用后端 merchant 索引，不依赖 Android 本地拼接。
- `POST /v1/orders/checkout-intent` 由后端生成 `checkoutIntentId`、`offerId`、`amountText` 和 `status=CREATED`。

### 互动接口

```text
POST /v1/ads/{adId}/like
DELETE /v1/ads/{adId}/like
POST /v1/ads/{adId}/collect
DELETE /v1/ads/{adId}/collect
POST /v1/ads/{adId}/share
POST /v1/ads/{adId}/click
POST /v1/ads/{adId}/exposure
```

API 自动化和并发验证可选传 `X-NBN-User-Id` 指定本次请求的当前用户。不传该头时仍沿用 `/v1/auth/login` 写入的默认会话，兼容现有 Stitch WebView 主流程。

互动接口返回更新后的 `interactionState` 和 `stats`。用户级收藏/点赞列表不能只依赖广告计数，必须按 `userId + adId + action` 建记录。

真实数据规则：

- 当前账号固定解析为 `GET /v1/users/me -> user_current_001`。
- `liked`、`collected`、`shared` 从当前用户是否存在于对应关系数组派生。
- `likeCount`、`collectCount`、`shareCount` 从关系数组长度派生，重复点赞/收藏/分享不重复加数。
- `clickCount`、`exposureCount` 是广告事件计数，仍按事件累加。

### AI 搜索接口

```text
POST /v1/ai/search
GET /v1/ai/search/suggestions
GET /v1/ai/search/sessions/{sessionId}
POST /v1/ai/search/sessions/{sessionId}/messages
POST /v1/ai/ads/{adId}/summary
POST /v1/ai/ads/{adId}/tags
POST /v1/ai/ads/rerank
```

AI 搜索响应必须包含：

- `session`：会话 ID、原始 query、时间和上下文。
- `messages`：用户消息与 AI 回复。
- `results`：广告 ID、标题、推荐理由、价格、图片、按钮文案。
- `recommendationReason`：匹配标签、性价比解释和排序分。
- `fallback`：AI 不可用时的降级文案和默认结果。

后端实现规则：

- Android 不直接持有云端模型密钥。
- 云端模型配置只能从环境变量或运行环境读取，当前使用：
  - `NBN_AI_API_KEY`
  - `NBN_AI_ENDPOINT`
  - `NBN_AI_MODEL`
- 云端 provider 使用 OpenAI-compatible `messages` JSON body；具体 endpoint 和 model 不写死在 Android。
- 云端返回成功时，后端返回 `source=remote_ai`。
- 未配置云端模型时，后端返回 `provider.source=rule_fallback` 和 `fallback.fallbackReason=AI_PROVIDER_NOT_CONFIGURED`。
- 云端请求失败或响应不可解析时，后端返回 `fallback.fallbackReason=AI_PROVIDER_ERROR`，不得阻塞 App 搜索和详情展示。
- `AiSearchSession` 和 `AiSearchMessage` 必须由后端保存，`GET /v1/ai/search/sessions/{sessionId}` 可读取历史。
- `POST /v1/ai/search/sessions/{sessionId}/messages` 必须追加用户消息和 AI/fallback 回复。
- `POST /v1/ai/ads/{adId}/summary` 返回 `summary`、`source`、`fallbackReason`，配置云端时优先 remote。
- `POST /v1/ai/ads/{adId}/tags` 返回可解释标签列表，配置云端时优先 remote 标签分类。
- `POST /v1/ai/ads/rerank` 返回带 `rankScore` 的排序结果。

### 评论与评价接口

```text
GET /v1/ads/{adId}/reviews?cursor=&limit=
POST /v1/ads/{adId}/reviews
POST /v1/reviews/{reviewId}/like
DELETE /v1/reviews/{reviewId}/like
GET /v1/comments?targetType=ad&targetId={adId}
POST /v1/comments
DELETE /v1/comments/{commentId}
```

评论必须支持 `targetType`、`targetId`、`parentCommentId`、`userId`、`nickname` 和 `userAvatarUrl`，用于广告评论、帖子评论和评价回复共用同一结构。

后端实现状态：

- `GET /v1/ads/{adId}/reviews` 支持 `cursor` 和 `limit` 分页。
- `POST /v1/ads/{adId}/reviews` 会写入后端状态，并绑定当前真实用户。
- `POST/DELETE /v1/reviews/{reviewId}/like` 会按当前用户关系更新评价 `liked` 和 `likeCount`。
- `GET/POST/DELETE /v1/comments` 共用同一 `Comment` 结构，支持广告评论、帖子评论和评价回复。
- 新增或删除 `targetType=ad` 的评论时，后端同步更新对应广告 `stats.commentCount`。

Android 实现规则：

- `RepositoryProvider.getBackendReviewDataSource()` 提供 App 侧评论评价 HTTP client。
- `BackendReviewDataSource` 直接调用评价分页、评价发布、评价点赞、评论查询和评论发布接口。
- 该 client 返回 `StitchDetailModels.Review`、`StitchDetailModels.Comment` 以及分页 wrapper，便于详情页后续从 WebView payload 迁移到原生拉取。

### 消息接口

```text
GET /v1/notifications/summary
GET /v1/notifications?type=like&cursor=&limit=
POST /v1/notifications/read
POST /v1/notifications/read-all
GET /v1/conversations?cursor=&limit=
GET /v1/conversations/{conversationId}/messages?cursor=&limit=
POST /v1/conversations/{conversationId}/messages
GET /v1/ai-assistant/digest
```

消息页数据分成未读摘要、通知流、私信会话、会话消息和 AI 助手摘要。通知读取失败不得影响广告互动状态。

后端实现状态：

- `GET /v1/notifications/summary` 基于后端通知 `read` 状态实时计算未读数。
- `POST /v1/notifications/read` 支持按 `notificationIds` 或 `type` 标记已读。
- `POST /v1/notifications/read-all` 会清空当前后端通知未读状态。
- `GET/POST /v1/conversations/{conversationId}/messages` 支持会话消息读取和追加。
- `GET /v1/ai-assistant/digest` 返回后端 AI 助手摘要 seed。

Android 实现规则：

- `RepositoryProvider.getBackendMessageDataSource()` 提供 App 侧消息域 HTTP client。
- `BackendMessageDataSource` 覆盖未读摘要、通知分页、标记已读、会话分页、会话消息分页、发送消息和 AI 助手摘要。
- 该 client 返回 `StitchMessageModels` 以及分页/写入结果 wrapper；接口失败统一抛出 `RemoteAdException`。

### 我的页接口

```text
POST /v1/users
GET /v1/users/me
PATCH /v1/users/me
GET /v1/users/me/stats
GET /v1/users/me/achievements
GET /v1/users/{userId}/posts?tab=notes
GET /v1/users/{userId}/posts?tab=collections
GET /v1/users/{userId}/posts?tab=liked
POST /v1/users/me/posts
PATCH /v1/users/me/posts/{postId}
DELETE /v1/users/me/posts/{postId}
GET /v1/users/{userId}/followers
GET /v1/users/{userId}/following
POST /v1/users/{userId}/follow
DELETE /v1/users/{userId}/follow
```

`UserProfile` 必须支持创建和编辑：

- `nickname`
- `avatarUrl`
- `level`
- `bio`
- `personalInfo`
- `stats`
- `achievements`

`ProfilePost.tab` 只能使用 `notes`、`collections`、`liked`。发布笔记、收藏内容、点赞内容必须能回到同一个用户中心列表。

真实数据规则：

- `GET /v1/users/me` 默认返回 `user_current_001`，该当前账号初始 `followingCount=0`、`followerCount=0`、`postCount=0`。
- `POST /v1/users` 创建的新账号也必须默认 0 关注、0 粉丝、0 笔记。
- `postCount` 从 `posts.userId == 当前用户 && tab=notes` 派生。
- `followingCount` 从当前用户主动关注关系派生，`followerCount` 从被关注关系派生。
- `likedAndCollectedCount` 从广告点赞/收藏关系派生。
- 非广告 `posts[]` 必须带 `userId` 和 `author`，且 `userId` 必须能在 `userProfile` 或 `users[]` 中查到。

Android 实现规则：

- `RepositoryProvider.getBackendUserDataSource()` 提供 App 侧用户域 HTTP client。
- `BackendUserDataSource` 直接调用后端 `/v1/users...` 契约，覆盖创建用户、读取/编辑当前用户、笔记 CRUD、关注/取消关注、粉丝/关注列表。
- `HttpApiClient` 支持 `GET`、`POST`、`PATCH`、`DELETE`；`PATCH` 使用 raw HTTP 路径保持真实 HTTP method，不降级为伪 POST。
- 无后端或响应异常时，用户域 native client 抛出 `RemoteAdException`；WebView 我的页仍可通过 Stitch page payload 的本地数据 fallback 保证演示可打开。

### 配置、素材和埋点接口

```text
GET /v1/config/app
GET /v1/assets/manifest
GET /v1/design-content/home
POST /v1/events/batch
POST /v1/exposures/batch
GET /v1/analytics/summary
```

`RemoteConfig` 必须覆盖功能开关、频道配置、AI 是否开启和请求超时。`AssetManifest` 必须能把 Stitch 本地素材和远程 URL 对齐，避免 UI 使用无法追溯的图片或视频。

后端实现状态：

- `GET /v1/config/app` 返回 `remoteConfig`。
- `GET /v1/assets/manifest` 返回 Stitch 本地素材与远程 URL 映射。
- `GET /v1/design-content/home` 返回首页数据驱动内容和远程配置。
- `POST /v1/events/batch` 与 `POST /v1/exposures/batch` 在后端内存累计批次。
- `GET /v1/analytics/summary` 返回配置口径和当前累计事件、曝光、有效曝光数量。

Android 实现规则：

- `RepositoryProvider.getBackendPlatformDataSource()` 提供 App 侧配置、素材和统计 HTTP client。
- `BackendPlatformDataSource` 覆盖 `remoteConfig`、`assetManifest`、首页设计内容、事件批量上报、曝光批量上报和统计摘要。
- 事件/曝光上报使用 `StitchConfigModels.StitchAnalyticsEvent` 和 `ExposureEvent`，响应返回 accepted/total/valid 计数。

### Android 数据桥接口

当前 Android WebView 主路径通过后端页面 payload 接入真实 HTTP，原生 `AdRepository` 通过 `BackendRemoteAdDataSource` 接入 feed/detail/interaction API。

```text
GET /v1/stitch/pages/home
GET /v1/stitch/pages/search
GET /v1/stitch/pages/detail
GET /v1/stitch/pages/messages
GET /v1/stitch/pages/profile
```

响应仍使用统一 envelope，`data` 结构与本地 `stitch_data` payload 对齐：

- `home`：`homeFeed` + `appConfig`
- `search`：`search` + `appConfig`
- `detail`：`details` + `reviews` + `appConfig`
- `messages`：`messages` + `appConfig`
- `profile`：`profile` + `appConfig`

Android 接入规则：

- Android WebView 主路径只渲染后端 `/v1/stitch/pages/*` payload；后端未连接时显示后端不可用状态，不再展示前端静态假业务数据。
- `stitch_data` 仍保留为接口契约镜像、单测 fixture 和 seed 同步校验来源，不能作为演示主路径的可见业务数据。
- `BackendRemoteAdDataSource` 请求 `/v1/feed`、`/v1/ads/{adId}` 和互动 API，失败时由 `DefaultAdRepository` fallback 到 `MockAdRepository`。
- 模拟器默认使用 `http://10.0.2.2:8080`，真机可通过 `adb reverse tcp:8080 tcp:<backendPort>` 使用 `http://127.0.0.1:8080`。
