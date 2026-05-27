# 接口契约

## 广告数据模型

```json
{
  "id": "ad_001",
  "title": "轻量跑鞋新品首发",
  "brand": "NBN Sports",
  "channel": "精选",
  "contentType": "LARGE_IMAGE",
  "summary": "适合通勤和夜跑的轻量缓震跑鞋。",
  "imageUrl": "https://example.invalid/images/ad_001.jpg",
  "videoUrl": null,
  "tags": ["运动", "学生党", "性价比"],
  "stats": {
    "exposureCount": 0,
    "clickCount": 0
  },
  "interaction": {
    "liked": false,
    "collected": false
  }
}
```

## 分页请求

```json
{
  "channel": "精选",
  "cursor": "page_1",
  "pageSize": 10
}
```

## 分页响应

```json
{
  "items": [],
  "nextCursor": "page_2",
  "hasMore": true
}
```

## AI 摘要请求

```json
{
  "adId": "ad_001",
  "title": "轻量跑鞋新品首发",
  "brand": "NBN Sports",
  "description": "适合通勤和夜跑的轻量缓震跑鞋。",
  "promptVersion": "summary_v1"
}
```

## AI 摘要响应

```json
{
  "adId": "ad_001",
  "summary": "轻量缓震跑鞋，适合通勤夜跑。",
  "source": "remote_ai",
  "cached": false
}
```

## AI 标签响应

```json
{
  "adId": "ad_001",
  "tags": ["运动", "通勤", "学生党", "性价比"],
  "source": "remote_ai",
  "cached": false
}
```

## 对话式搜索请求

```json
{
  "query": "找适合学生党的运动广告",
  "channels": ["精选", "电商", "本地"],
  "limit": 10
}
```

## 对话式搜索响应

```json
{
  "answer": "为你找到偏运动和高性价比的广告。",
  "matchedAdIds": ["ad_001"],
  "fallback": false
}
```

## 统计事件

```json
{
  "event": "ad_exposure",
  "adId": "ad_001",
  "visibleRatio": 0.72,
  "durationMs": 1200,
  "timestamp": 1760000000000
}
```
