# AI 与数据设计

## 目标

AI 能力服务 Feed、详情和搜索展示，但不阻断主流程。真实 AI 不可用时，摘要和标签必须来自缓存、规则或 Mock 降级。

## 接口

```java
AiResponse<String> getAiSummary(String adId);
AiResponse<List<String>> getAiTags(String adId);
```

`AiResponse<T>` 包含：

- `value`
- `source`
- `cached`
- `message`
- `error`

## 来源

| source | 说明 |
| --- | --- |
| `remote_ai` | 远程生成成功 |
| `cache` | 同一内容和 prompt 版本命中缓存 |
| `rule_fallback` | 根据标题、描述、品牌、频道、本地规则生成 |
| `mock_fallback` | 使用 Mock 数据中已有摘要或标签 |

## 缓存

缓存 Key：

```text
adId + contentHash + promptVersion
```

当前 prompt 版本：

- 摘要：`summary_v1`
- 标签：`tagging_v1`

内容变化后 `contentHash` 改变，旧缓存不会命中。

## 质量约束

- 摘要不超过 40 个中文字符。
- 标签数量 3-5 个。
- 单个标签不超过 6 个中文字符。
- 标签只能从标题、品牌、描述、频道、已有标签和内容类型推断。
- 不编造价格、疗效和绝对化营销承诺。

## 降级路径

1. 先查缓存。
2. 默认使用离线 demo remote 生成器；后续可替换为真实网络或本地模型服务。
3. 远程失败或空结果时使用 Mock 摘要/标签。
4. Mock 内容不足时使用规则降级。

## 当前默认演示

- `DefaultAdAiService(repository)` 默认注入 `DemoRemoteSummaryGenerator` 与 `DemoRemoteTagGenerator`。
- 普通广告第一次请求返回 `remote_ai`，同一服务实例第二次请求返回 `cache`。
- 弱内容广告会触发 demo remote 失败，继续走 `mock_fallback` 或 `rule_fallback`。
- 该路径不依赖外部网络、密钥、Token 或额外 Gradle 依赖。

## 验证

- `CachedSummaryServiceTest.summarizeUsesRemoteOnceThenCache`
- `CachedSummaryServiceTest.summarizeFallsBackWhenRemoteFails`
- `CachedSummaryServiceTest.contentChangeInvalidatesCache`
- `CachedTaggingServiceTest.generateTagsNormalizesRemoteOutputThenCaches`
- `CachedTaggingServiceTest.generateTagsFallsBackToAdTags`
- `DefaultAdAiServiceTest.defaultServiceUsesDemoRemoteOnFirstRequest`
- `DefaultAdAiServiceTest.defaultServiceCachesSecondRequestInSameInstance`
- `DefaultAdAiServiceTest.defaultServiceFallsBackForWeakDemoContent`
