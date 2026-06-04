# 对接 API 文档

## 1. 对接原则

人不直接读取 Mock JSON，不在 UI 层硬编码广告数据，不在 UI 层拼真实后端字段。UI 只通过下面两类入口拿数据：

1. 原生 Android 页面使用 `RepositoryProvider.getRepository(context)`、`RepositoryProvider.getAdAiService(context)` 和 `RepositoryProvider.getAnalyticsTracker()`。
2. Stitch WebView 页面使用 Android 注入的后端页面 payload，对应后端 `GET /v1/stitch/pages/{pageName}`。

数据层会负责 Remote 优先、Mock fallback、AI fallback、分页、错误状态、互动状态同步和 HTTP 解析。UI 只负责展示、交互触发和根据状态切换加载态、空态、错误态。

## 2.  必接 Java 入口

### 2.1 广告数据入口

```java
AdRepository repository = RepositoryProvider.getRepository(context);

DataResult<PageResult<AdItem>> feed = repository.loadAds(PageRequest.firstPage("featured", 10));
DataResult<AdItem> detail = repository.getAdById("ad_001");
DataResult<PageResult<AdItem>> search = repository.searchAds(SearchRequest.keyword("学生党"));
DataResult<AdItem> updated = repository.updateInteraction("ad_001", InteractionAction.TOGGLE_LIKE);
```

`AdRepository` 当前稳定方法：

```java
DataResult<PageResult<AdItem>> loadAds(PageRequest request);
DataResult<AdItem> getAdById(String adId);
DataResult<PageResult<AdItem>> searchAds(SearchRequest request);
DataResult<AdItem> updateInteraction(String adId, InteractionAction action);
```

兼容旧 UI 的辅助方法仍存在：`getInitialAds()`、`getAdsByChannel(channel)`、`getAdsPage(request)`、`getAdsByTag(tag)`、`searchByKeyword(keyword)`。新 UI 优先使用上面四个正式方法。

### 2.2 AI 摘要和标签入口

```java
AdAiService aiService = RepositoryProvider.getAdAiService(context);

AiResponse<String> summary = aiService.getAiSummary("ad_001");
AiResponse<List<String>> tags = aiService.getAiTags("ad_001");
```

规则：

- 默认 Android 路径使用 `BackendAdAiService`。
- 后端 AI 不可用、响应异常或返回空结果时，会 fallback 到本地 `DefaultAdAiService`。
- UI 可以展示 `response.getValue()`；`response.getSource()` 只用于状态标签。

### 2.3 统计入口

```java
AnalyticsTracker tracker = RepositoryProvider.getAnalyticsTracker();

tracker.trackExposure(adId, channelId, "feed");
tracker.trackClick(adId, channelId, "feed");
tracker.trackLike(adId, channelId, "detail");
tracker.trackCollect(adId, channelId, "detail");
tracker.trackShare(adId, channelId, "detail");
tracker.trackSearch(keyword, "search");
```

统计失败不能阻塞 UI，也不能改变 Repository 的互动状态。

## 3. 统一返回结构

### 3.1 Android `DataResult<T>`

```java
enum Status {
    SUCCESS,
    EMPTY,
    TIMEOUT,
    PARSE_ERROR,
    REMOTE_ERROR,
    FALLBACK
}
```

字段：

| 字段 | 类型 | UI 用法 |
|---|---|---|
| `status` | enum | 判断成功、空态、错误态、降级态 |
| `data` | T | 页面实际渲染数据 |
| `source` | String | `backend`、`mock`、`remote` 等来源提示 |
| `message` | String | 空态或错误态辅助文案 |
| `error` | Throwable | UI 不直接展示，调试日志可用 |

状态展示建议：

| 状态 | A 的展示方式 |
|---|---|
| `SUCCESS` | 正常渲染数据 |
| `FALLBACK` | 正常渲染数据，可弱提示“已展示可用内容” |
| `EMPTY` | 展示空态，不追加旧数据 |
| `TIMEOUT` | 展示网络超时错误 |
| `PARSE_ERROR` | 展示数据解析失败错误 |
| `REMOTE_ERROR` | 展示服务异常错误 |

### 3.2 后端 HTTP Envelope

所有后端接口返回统一 envelope：

```json
{
  "requestId": "req-xxx",
  "code": "OK",
  "message": "ok",
  "data": {}
}
```

错误码：

| code | 含义 |
|---|---|
| `OK` | 成功 |
| `BAD_REQUEST` | 参数或请求体错误 |
| `NOT_FOUND` | 资源不存在 |
| `METHOD_NOT_ALLOWED` | HTTP method 不匹配 |
| `INTERNAL_ERROR` | 后端内部异常 |

Android HTTP client 会把非 `OK`、缺失 `data`、非法 JSON 映射为 `RemoteAdException`，UI 不需要自己解析 envelope。

## 4. 分页契约

### 4.1 请求模型 `PageRequest`

```json
{
  "channel": "featured",
  "cursor": "page_1",
  "pageSize": 10,
  "refresh": true,
  "sourcePage": "feed"
}
```

规则：

- `PageRequest.FIRST_CURSOR` 固定为 `page_1`。
- `PageRequest.DEFAULT_PAGE_SIZE` 固定为 `10`。
- `PageRequest.firstPage(channel, 10)` 表示刷新或首屏。
- `PageRequest.nextPage(channel, nextCursor, 10)` 表示加载更多。
- `nextCursor == null` 或 `hasMore == false` 时停止加载更多。
- 刷新必须替换列表，不能追加旧数据。

### 4.2 响应模型 `PageResult<T>`

```json
{
  "items": [],
  "currentCursor": "page_1",
  "nextCursor": "page_2",
  "hasMore": true,
  "pageNumber": 1,
  "pageSize": 10,
  "totalCount": 30,
  "dataSource": "backend"
}
```

字段说明：

| 字段 | 类型 | UI 用法 |
|---|---|---|
| `items` | array | 当前页数据 |
| `currentCursor` | string | 当前页 cursor |
| `nextCursor` | string/null | 下一页 cursor |
| `hasMore` | boolean | 控制加载更多 |
| `pageNumber` | int | 调试或分页标识 |
| `pageSize` | int | 当前页大小 |
| `totalCount` | int | 总数展示 |
| `dataSource` | string | 数据来源 |

## 5. 广告模型 `AdItem`

UI 渲染广告卡片、详情页、搜索结果都使用 `AdItem`。

```json
{
  "id": "ad_001",
  "title": "轻量跑鞋新品首发",
  "brand": "NBN Sports",
  "channel": "featured",
  "channelId": "featured",
  "description": "适合校园通勤、夜跑和日常训练。",
  "summary": "轻量缓震跑鞋，适合通勤夜跑。",
  "imageUrl": "https://cdn.example/runner.jpg",
  "thumbnailUrl": "https://cdn.example/runner-thumb.jpg",
  "videoUrl": null,
  "offerText": "限时满减",
  "ctaText": "查看详情",
  "skuText": "标准款 / 默认尺码",
  "ratingText": "4.8 分",
  "deliveryText": "48 小时内发货",
  "stockText": "现货",
  "similarItems": ["ad_002", "ad_003"],
  "distanceText": "1.2km",
  "districtText": "五道口",
  "addressText": "校园东门附近",
  "businessHoursText": "10:00-22:00",
  "navigationText": "查看地图",
  "assetTheme": "commerce",
  "visualLabel": "运动通勤场景",
  "ctaIntent": "product",
  "contentType": "LARGE_IMAGE",
  "tags": ["运动", "学生党", "通勤"],
  "interactionState": {
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
  "contentHash": "ad_001"
}
```

### 5.1 卡片必用字段

| 字段 | 用途 |
|---|---|
| `id` | 跳详情、互动、埋点主键 |
| `title` | 卡片标题 |
| `brand` | 品牌名 |
| `channel` / `channelId` | 频道展示和统计 |
| `summary` | 卡片短文案 |
| `imageUrl` / `thumbnailUrl` | 图片 |
| `videoUrl` | 视频素材 |
| `contentType` | 大图、小图、视频卡 |
| `tags` | 标签胶囊 |
| `interactionState` | 点赞、收藏按钮状态 |
| `stats` | 热度、点赞、收藏、分享 |

### 5.2 详情页可选增强字段

| 字段 | 用途 |
|---|---|
| `description` | 详情长文案 |
| `offerText` | 商品权益 |
| `ctaText` | 主按钮文案 |
| `skuText` | SKU 展示 |
| `ratingText` | 用户评价概览 |
| `deliveryText` | 配送承诺 |
| `stockText` | 库存状态 |
| `similarItems` | 相似推荐 ID |
| `distanceText` / `districtText` | 本地服务位置 |
| `addressText` / `businessHoursText` | 门店信息 |
| `navigationText` | 到店入口文案 |
| `assetTheme` | fallback 素材主题 |
| `visualLabel` | 图片/视频说明 |
| `ctaIntent` | 主按钮语义 |

### 5.3 枚举

`AdContentType`：

```text
LARGE_IMAGE
SMALL_IMAGE
VIDEO
```

`InteractionAction`：

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

## 6. 互动规则

UI 触发互动时调用：

```java
repository.updateInteraction(adId, InteractionAction.TOGGLE_LIKE);
repository.updateInteraction(adId, InteractionAction.TOGGLE_COLLECT);
repository.updateInteraction(adId, InteractionAction.CLICK);
repository.updateInteraction(adId, InteractionAction.EXPOSE);
repository.updateInteraction(adId, InteractionAction.SHARE);
```

规则：

- 点赞和收藏推荐使用 `TOGGLE_LIKE`、`TOGGLE_COLLECT`。
- 数据层会先读当前状态，再决定 POST 还是 DELETE。
- 返回值是更新后的 `AdItem`，UI 应用返回值刷新按钮状态和计数。
- `CLICK`、`EXPOSE`、`SHARE` 更新 `stats`，不改变 liked/collected。
- 不存在广告 ID 时返回 `DataResult.EMPTY` 或后端 404。

## 7. 搜索契约

### 7.1 Android `SearchRequest`

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

快捷构造：

```java
SearchRequest.keyword("学生党");
SearchRequest.tag("运动");
```

搜索匹配字段包括 `title`、`brand`、`description`、`summary`、`tags`。当前 `BackendRemoteAdDataSource.searchAds()` 会先请求 feed，再在 Android 侧过滤；AI 搜索页可使用后端 `POST /v1/ai/search`。

### 7.2 AI 搜索响应核心字段

AI 搜索响应必须让 UI 能展示：

| 字段 | 用途 |
|---|---|
| `session` | 会话 ID、原始 query、时间、上下文 |
| `messages` | 用户消息和 AI 回复 |
| `results` | 推荐广告列表 |
| `recommendationReason` | 匹配标签、解释、排序分 |
| `fallback` | AI 不可用时的降级说明和默认结果 |

## 8. AI 摘要与标签

`AiResponse<T>` 字段：

| 字段 | 类型 | UI 用法 |
|---|---|---|
| `value` | T | 摘要文本或标签数组 |
| `source` | enum | 来源标签，可选展示 |
| `cached` | boolean | 是否复用缓存 |
| `message` | string | 降级说明 |
| `error` | Throwable | UI 不直接展示 |

`AiOutputSource`：

```text
remote_ai
cache
rule_fallback
mock_fallback
```

展示规则：

- `REMOTE_AI`：可显示“AI 已生成”。
- `CACHE`：可显示“已复用推荐结果”。
- `RULE_FALLBACK` / `MOCK_FALLBACK`：可显示“本地增强结果”。
- 摘要建议不超过 40 个中文字符。
- 标签建议 3-5 个，每个标签不超过 6 个中文字符。

## 9. 图片和视频

UI 图片加载应使用 `AdMediaLoader`：

```java
AdMediaLoader.loadFeedImage(imageView, adItem);
AdMediaLoader.loadDetailImage(imageView, adItem);
AdMediaLoader.loadSearchThumbnail(imageView, thumbnailUrl, imageUrl, fallbackResId);
```

规则：

- `AdMediaLoader` 只加载 `https://` 图片 URL。
- 非 https、空 URL、加载失败时自动展示 fallback drawable。
- fallback 会根据 `contentType`、`assetTheme`、标题和标签选择本地素材。
- 视频素材使用 `videoUrl` 和 `contentType == VIDEO` 判断；本地 raw URI 可通过 `AdMediaResources.rawResourceUri()` 转换。

## 10. 后端 HTTP API 总表

基础地址：

| 场景 | baseUrl |
|---|---|
| Android 模拟器访问本机后端 | `http://10.0.2.2:8080` |
| 真机 adb reverse 后访问本机后端 | `http://127.0.0.1:8080` |
| 自定义 | JVM property `nbn.api.baseUrl` |

默认超时和 fallback：

| 配置 | 默认 |
|---|---|
| connect timeout | 900ms 或 property `nbn.api.connectTimeoutMs` |
| read timeout | 1500ms 或 property `nbn.api.readTimeoutMs` |
| retry count | 默认候选地址各试一次 |
| mock fallback | `true` |

### 10.1 Feed 和广告详情

| Method | Path | 用途 |
|---|---|---|
| GET | `/v1/feed/channels` | 获取频道 |
| GET | `/v1/feed?channel={channel}&cursor={cursor}&limit={limit}` | 获取广告列表 |
| GET | `/v1/ads/{adId}` | 获取单条广告 |
| GET | `/v1/ads/{adId}/detail` | 获取详情页长内容 |
| GET | `/v1/ads/{adId}/related` | 获取相关推荐 |
| GET | `/v1/ads/{adId}/commerce` | 获取商详/优惠/卖点 |
| GET | `/v1/merchants/{merchantId}` | 获取商家 |
| GET | `/v1/merchants/{merchantId}/nearby` | 获取附近商家 |
| POST | `/v1/orders/checkout-intent` | 创建下单意图 |

### 10.2 互动

| Method | Path | 对应 `InteractionAction` |
|---|---|---|
| POST | `/v1/ads/{adId}/like` | `LIKE` |
| DELETE | `/v1/ads/{adId}/like` | `UNLIKE` |
| POST | `/v1/ads/{adId}/collect` | `COLLECT` |
| DELETE | `/v1/ads/{adId}/collect` | `UNCOLLECT` |
| POST | `/v1/ads/{adId}/share` | `SHARE` |
| POST | `/v1/ads/{adId}/click` | `CLICK` |
| POST | `/v1/ads/{adId}/exposure` | `EXPOSE` |

### 10.3 AI

| Method | Path | 用途 |
|---|---|---|
| POST | `/v1/ai/search` | AI 对话搜索 |
| GET | `/v1/ai/search/suggestions` | 搜索建议 |
| GET | `/v1/ai/search/sessions/{sessionId}` | 读取搜索会话 |
| POST | `/v1/ai/search/sessions/{sessionId}/messages` | 追加会话消息 |
| POST | `/v1/ai/ads/{adId}/summary` | 广告摘要 |
| POST | `/v1/ai/ads/{adId}/tags` | 广告标签 |
| POST | `/v1/ai/ads/rerank` | 广告重排 |

云端 AI 配置只存在后端运行环境，Android 不保存模型密钥：

```text
NBN_AI_API_KEY
NBN_AI_ENDPOINT
NBN_AI_MODEL
```

未配置或请求失败时，后端返回规则降级，App 仍可展示。

### 10.4 评论和评价

| Method | Path | 用途 |
|---|---|---|
| GET | `/v1/ads/{adId}/reviews?cursor={cursor}&limit={limit}` | 评价分页 |
| POST | `/v1/ads/{adId}/reviews` | 发布评价 |
| POST | `/v1/reviews/{reviewId}/like` | 点赞评价 |
| DELETE | `/v1/reviews/{reviewId}/like` | 取消点赞评价 |
| GET | `/v1/comments?targetType={type}&targetId={id}` | 查询评论 |
| POST | `/v1/comments` | 发布评论 |
| DELETE | `/v1/comments/{commentId}` | 删除评论 |

评论支持 `targetType`、`targetId`、`parentCommentId`、`userId`、`nickname`、`userAvatarUrl`，用于广告评论、帖子评论和评价回复。

### 10.5 消息

| Method | Path | 用途 |
|---|---|---|
| GET | `/v1/notifications/summary` | 未读摘要 |
| GET | `/v1/notifications?type={type}&cursor={cursor}&limit={limit}` | 通知分页 |
| POST | `/v1/notifications/read` | 标记部分已读 |
| POST | `/v1/notifications/read-all` | 全部已读 |
| GET | `/v1/conversations?cursor={cursor}&limit={limit}` | 会话分页 |
| GET | `/v1/conversations/{conversationId}/messages?cursor={cursor}&limit={limit}` | 会话消息 |
| POST | `/v1/conversations/{conversationId}/messages` | 发送消息 |
| GET | `/v1/ai-assistant/digest` | AI 助手摘要 |

### 10.6 用户和我的页

| Method | Path | 用途 |
|---|---|---|
| POST | `/v1/users` | 创建用户 |
| GET | `/v1/users/me` | 当前用户 |
| PATCH | `/v1/users/me` | 编辑当前用户 |
| GET | `/v1/users/me/stats` | 当前用户统计 |
| GET | `/v1/users/me/achievements` | 成就 |
| GET | `/v1/users/{userId}/posts?tab={tab}` | 用户内容列表 |
| POST | `/v1/users/me/posts` | 发布笔记 |
| PATCH | `/v1/users/me/posts/{postId}` | 编辑笔记 |
| DELETE | `/v1/users/me/posts/{postId}` | 删除笔记 |
| GET | `/v1/users/{userId}/followers` | 粉丝 |
| GET | `/v1/users/{userId}/following` | 关注 |
| POST | `/v1/users/{userId}/follow` | 关注用户 |
| DELETE | `/v1/users/{userId}/follow` | 取消关注 |

`ProfilePost.tab` 只允许 `notes`、`collections`、`liked`。

### 10.7 配置、素材和统计上报

| Method | Path | 用途 |
|---|---|---|
| GET | `/v1/config/app` | App 远程配置 |
| GET | `/v1/assets/manifest` | 素材映射 |
| GET | `/v1/design-content/home` | 首页设计内容 |
| POST | `/v1/events/batch` | 事件批量上报 |
| POST | `/v1/exposures/batch` | 曝光批量上报 |
| GET | `/v1/analytics/summary` | 统计摘要 |

### 10.8 Stitch WebView 页面数据

| Method | Path | 对应页面 |
|---|---|---|
| GET | `/v1/stitch/pages/home` | 首页 |
| GET | `/v1/stitch/pages/search` | 搜索 |
| GET | `/v1/stitch/pages/detail` | 详情 |
| GET | `/v1/stitch/pages/messages` | 消息 |
| GET | `/v1/stitch/pages/profile` | 我的 |

payload 结构：

| page | `data` 内容 |
|---|---|
| `home` | `homeFeed` + `appConfig` |
| `search` | `search` + `appConfig` |
| `detail` | `details` + `reviews` + `appConfig` |
| `messages` | `messages` + `appConfig` |
| `profile` | `profile` + `appConfig` |

WebView 主路径应渲染后端 payload。`app/src/main/assets/stitch_data/*.json` 保留为契约镜像、seed 和测试 fixture，不作为演示主路径的可见业务数据。

### 10.9 Auth

| Method | Path | 用途 |
|---|---|---|
| GET | `/v1/auth/session` | 当前会话 |
| POST | `/v1/auth/register` | 注册 |
| POST | `/v1/auth/login` | 登录 |
| POST | `/v1/auth/logout` | 登出 |

自动化和并发测试可选传 `X-NBN-User-Id` 指定当前用户。不传时使用默认会话。

## 11. HTTP 示例

### 11.1 Feed

请求：

```http
GET /v1/feed?channel=featured&cursor=page_1&limit=10
```

响应：

```json
{
  "requestId": "req-test",
  "code": "OK",
  "message": "ok",
  "data": {
    "channel": "featured",
    "cursor": "page_1",
    "nextCursor": "page_2",
    "hasMore": true,
    "totalCount": 30,
    "items": [
      {
        "adId": "ad_001",
        "title": "Runner Launch",
        "subtitle": "Campus commute",
        "description": "Light runner for student commute",
        "cover": {
          "url": "https://cdn.example/runner.jpg"
        },
        "video": null,
        "adType": "LARGE_IMAGE",
        "brand": "NBN Sports",
        "category": "sport",
        "tags": [
          {"name": "sport"},
          {"name": "student"}
        ],
        "stats": {
          "likeCount": 320,
          "collectCount": 180,
          "shareCount": 46,
          "exposureCount": 1680,
          "clickCount": 268
        },
        "interactionState": {
          "liked": false,
          "collected": false
        }
      }
    ]
  }
}
```

Android 会把后端字段映射为 `AdItem`：

| 后端字段 | Android 字段 |
|---|---|
| `adId` | `id` |
| `subtitle` | `summary` |
| `cover.url` | `imageUrl` / `thumbnailUrl` |
| `adType` | `contentType` |
| `category` | `channel` |
| `tags[].name` | `tags` |
| `interactionState` | `interactionState` |
| `stats` | `stats` |

### 11.2 点赞

```http
POST /v1/ads/ad_001/like
```

成功后 UI 不直接使用该空响应，而是由 `BackendRemoteAdDataSource.updateInteraction()` 再请求 `GET /v1/ads/ad_001`，拿最新 `AdItem` 刷新状态。

### 11.3 AI 摘要

```http
POST /v1/ai/ads/ad_001/summary
```

```json
{
  "requestId": "req-ai",
  "code": "OK",
  "message": "ok",
  "data": {
    "summary": "轻量跑鞋适合学生通勤和夜跑。",
    "source": "remote_ai",
    "fallbackReason": ""
  }
}
```

### 11.4 AI 标签

```http
POST /v1/ai/ads/ad_001/tags
```

```json
{
  "requestId": "req-ai",
  "code": "OK",
  "message": "ok",
  "data": {
    "tags": ["运动", "学生党", "通勤"],
    "source": "remote_ai",
    "fallbackReason": ""
  }
}
```

## 12. 验收清单

接口联调至少检查：

- Feed 首屏使用 `loadAds(PageRequest.firstPage(...))`，不是直接读取 JSON。
- Feed 刷新时替换旧列表，加载更多时只追加新页。
- `EMPTY` 显示空态，`TIMEOUT`、`PARSE_ERROR`、`REMOTE_ERROR` 显示错误态。
- `FALLBACK` 仍正常展示数据，不当作失败。
- 点赞、收藏、分享、曝光、点击都通过 `updateInteraction()` 触发。
- 点赞和收藏按钮状态以返回的 `AdItem.interactionState` 为准。
- 计数以返回的 `AdItem.stats` 为准，不在 UI 自行永久加减。
- 详情页摘要和标签通过 `AdAiService` 获取。
- 图片加载使用 `AdMediaLoader`，非 https 或失败时有 fallback 图。
- WebView 页面渲染后端 `/v1/stitch/pages/*` payload，不展示静态假数据作为主路径。
- 搜索页普通广告搜索用 `searchAds()`，对话式 AI 搜索用 `/v1/ai/search` 或对应后端 client。
- UI 不保存 `NBN_AI_API_KEY`、`NBN_AI_ENDPOINT`、`NBN_AI_MODEL`。

## 13. 职责边界

人员 B 负责：

- 数据模型和接口契约。
- Repository、Remote、Mock fallback。
- AI 摘要、标签和搜索接口边界。
- 后端 endpoint 字段对齐。
- 错误码、分页、状态同步。

人员 A 负责：

- 页面结构、视觉、组件状态。
- 加载态、空态、错误态展示。
- 点击、点赞、收藏、搜索等交互触发。
- 根据 `AdItem`、`AiResponse`、Stitch payload 渲染 UI。

如果 UI 需要新增字段，先同步人员 B 更新本文件和对应模型，再改 UI。
