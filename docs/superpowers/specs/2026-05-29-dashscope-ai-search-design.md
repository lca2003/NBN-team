# DashScope AI Search Backend Design

## 背景

本仓库当前 Android 端已有对话式搜索 UI 和 Mock 搜索链路，`backend` 目录为空。现在需要在 `backend` 中新增一个完全独立的 Spring Boot 后端项目，用 Spring AI 接入 DashScope 大模型，为 Android 端提供最小可用的对话式广告搜索能力。

本规格只覆盖首版搜索接口，不覆盖摘要、标签、统计、登录、持久化和 Android 网络接入改造。

## 目标

- `backend` 是独立后端项目，不加入根 Gradle，也不影响 Android `:app` 构建。
- 使用 Java 21、Spring Boot、Spring AI Alibaba DashScope。
- 首版只开放 `POST /api/ai/search`。
- 广告数据只存在内存中，服务重启后恢复为启动时预置数据。
- DashScope 可用时由大模型生成搜索回答和匹配广告 ID。
- DashScope 不可用、未配置 API Key、超时、返回格式不可解析时，稳定回退到本地关键词搜索。
- 不在仓库中提交密钥、Token、签名证书或个人本地路径。

## 非目标

- 不实现数据库、Redis、文件持久化或缓存落盘。
- 不实现 `GET /api/ads`、摘要接口、标签接口和统计接口。
- 不实现用户会话、鉴权、限流、后台管理和在线编辑广告数据。
- 不改动 Android 端现有 UI 行为。
- 不把 DashScope API Key 写入 `application.yml` 或示例文件。

## 项目结构

```text
backend/
  settings.gradle
  build.gradle
  src/main/java/com/nbn/adfeed/backend/
    AdFeedBackendApplication.java
    ai/
      AiSearchController.java
      AiSearchService.java
      DashScopeSearchClient.java
      FallbackSearchService.java
    ad/
      AdItem.java
      AdMemoryService.java
    common/
      ApiExceptionHandler.java
  src/main/resources/
    application.yml
  src/test/java/com/nbn/adfeed/backend/
    ai/
      AiSearchControllerTest.java
      FallbackSearchServiceTest.java
```

包职责：

- `ai`：HTTP 搜索入口、搜索编排、DashScope 调用、本地降级。
- `ad`：内存广告模型和广告池。
- `common`：统一错误响应。

## 技术选择

- Java：21。
- 构建：独立 Gradle 项目。
- Web 框架：Spring Boot Web MVC。
- AI 接入：Spring AI Alibaba DashScope starter。
- 测试：JUnit Jupiter、Spring Boot Test、MockMvc。

版本策略：

- 优先使用 Spring AI Alibaba `1.1.2.3`，它依赖 Spring Boot `3.5.10`，比 Spring Boot 4 milestone 组合更适合课程项目的稳定实现。
- 如依赖解析发现兼容性问题，允许在不扩大功能范围的前提下调整到同一稳定线的最新补丁版本，并在实现说明中记录原因。

参考资料：

- Spring AI Alibaba DashScope 文档：`https://java2ai.com/integration/chatmodels/dashScope`
- Spring AI ChatClient 文档：`https://docs.spring.io/spring-ai/reference/api/chatclient.html`
- Maven Central DashScope starter metadata：`https://repo.maven.apache.org/maven2/com/alibaba/cloud/ai/spring-ai-alibaba-starter-dashscope/`

## 配置

`application.yml` 只放非敏感配置：

```yaml
server:
  port: 8080

spring:
  application:
    name: nbn-adfeed-backend
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY:}
      chat:
        options:
          model: qwen-plus
```

运行时通过环境变量注入：

```bash
export DASHSCOPE_API_KEY=your_key_here
./gradlew bootRun
```

如果 `DASHSCOPE_API_KEY` 为空，服务仍能启动，搜索接口自动使用本地降级结果。

## API 设计

### `POST /api/ai/search`

请求：

```json
{
  "query": "找适合学生党的运动广告",
  "channels": ["精选", "电商", "本地"],
  "limit": 10
}
```

字段规则：

- `query`：必填，去除首尾空白后长度为 1-200。
- `channels`：可选。为空或缺省时搜索所有频道；非空时只搜索这些频道。
- `limit`：可选，默认 10，取值范围 1-20。

响应：

```json
{
  "answer": "为你找到偏运动和高性价比的广告。",
  "matchedAdIds": ["ad_001"],
  "fallback": false
}
```

字段规则：

- `answer`：面向用户展示的简短中文回答。
- `matchedAdIds`：匹配广告 ID，最多返回 `limit` 个，只允许包含内存广告池中存在的 ID。
- `fallback`：`true` 表示使用本地降级搜索，`false` 表示 DashScope 结果通过校验。

错误响应：

```json
{
  "message": "query must not be blank"
}
```

错误状态：

- `400`：请求字段不合法。
- `500`：非 AI 调用类的服务内部错误。

DashScope 调用失败不返回 `500`，而是降级为 `fallback: true`。

## 内存广告池

`AdMemoryService` 在应用启动时构造固定广告列表，字段覆盖搜索所需信息：

```text
id
title
brand
channel
description
tags
```

首版预置数据不少于 6 条，覆盖频道：

- 精选
- 电商
- 本地

搜索时先根据 `channels` 过滤候选广告，再把候选广告交给 DashScope 或本地降级逻辑。

## 搜索流程

1. `AiSearchController` 校验请求字段。
2. `AiSearchService` 获取内存候选广告。
3. 如果候选广告为空，直接返回空匹配和说明文案，`fallback: true`。
4. 如果 DashScope API Key 为空，调用 `FallbackSearchService`。
5. 如果 DashScope API Key 存在，调用 `DashScopeSearchClient`。
6. 校验 DashScope 返回结果：
   - 必须是可解析 JSON。
   - `matchedAdIds` 必须都存在于候选广告中。
   - 返回数量不能超过 `limit`。
   - `answer` 不能为空。
7. 校验失败或调用异常时，调用 `FallbackSearchService`。
8. 返回统一响应。

## DashScope 提示词约束

系统提示词要求模型只根据给定广告候选列表回答，不编造广告 ID，不输出候选列表之外的 ID。

模型输出必须是纯 JSON：

```json
{
  "answer": "不超过 80 个中文字符的回答",
  "matchedAdIds": ["ad_001"]
}
```

实现只解析 JSON，不依赖自然语言解释。若模型输出 Markdown 代码块、额外说明或不可解析内容，则走本地降级。

## 本地降级搜索

`FallbackSearchService` 使用确定性规则：

- 对 `query`、`title`、`brand`、`description`、`tags` 做小写和空白归一。
- 命中标签、标题、品牌、描述时累积分数。
- 频道过滤在评分前完成。
- 分数相同按内存广告原始顺序返回。
- 没有任何命中时返回候选广告前 `limit` 条，并在 `answer` 中说明使用了默认推荐。

降级搜索必须无网络依赖，保证未配置 API Key 时接口仍可演示。

## 测试策略

先补最小自动化测试：

- `FallbackSearchServiceTest`
  - 能根据关键词匹配广告。
  - 能按频道过滤候选广告。
  - 没有命中时返回默认推荐。
- `AiSearchControllerTest`
  - `query` 为空返回 `400`。
  - 未配置 DashScope API Key 时接口返回 `fallback: true` 和匹配结果。

首版不在自动化测试中真实调用 DashScope，避免测试依赖外部网络和真实 API Key。

## 验证命令

后端验证：

```bash
cd backend
./gradlew test
./gradlew bootJar
```

Android 根工程提交前仍按仓库要求验证：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew assembleDebug
```

在 Windows 开发环境中，后端可使用当前 JDK 21 配置运行等价命令；最终提交说明需要记录实际执行过的命令和结果。

## 成功标准

- `backend` 可独立执行测试和打包。
- 未配置 `DASHSCOPE_API_KEY` 时，`POST /api/ai/search` 可返回降级搜索结果。
- 配置 `DASHSCOPE_API_KEY` 时，接口优先尝试 DashScope，并在失败时稳定降级。
- 响应结构与 Android 端现有对话式搜索契约一致。
- 仓库中不出现真实 API Key 或个人本地路径。
