# B0 基线审计执行计划

- 负责人：人员 B
- 阶段定位：开发前审计与契约准备
- 目标：把当前项目真实状态、A/C 依赖、B 后续改造清单和 B1/B2 输入一次性理清，避免后面接口返工或联调失控

## 1. B0 总目标

B0 要回答四个问题：

- 当前项目里 B 负责的代码已经有什么。
- 哪些代码可以保留，哪些必须改造，哪些需要新增。
- A 和 C 后续依赖 B 的字段、接口、状态、错误态是什么。
- B1/B2 是否可以开始冻结数据模型和 Repository 契约。

## 2. 推荐执行顺序

```text
B0.1 当前状态检查
-> B0.2 B 负责目录审计
-> A0 收集 A 的字段需求
-> C0 收集 C 的字段需求
-> B0.3 输出保留/改造/新增清单
-> B0.4 输出 B1/B2 前置契约草案
-> B0.5 B0 验收，通过后进入 B1/B2
```

后续团队节奏建议：

```text
B0 基线审计
-> A0 提 Feed/详情字段需求
-> C0 提搜索/统计字段需求
-> B1 数据模型补齐
-> B2 Repository v1 冻结
-> B3 Mock JSON 和数据覆盖矩阵
-> A1 Feed 骨架接入 Repository
-> C1 搜索基础接入 Repository
-> B4 分页/详情/搜索实现
-> B5 状态一致性
-> A2 刷新/加载/详情联调
-> C2 标签筛选/统计事件联调
-> B6 AI 摘要标签缓存降级
-> 集成联调
```

## 3. B0.1 当前状态检查

| 项目 | 内容 |
| --- | --- |
| 负责人 | B |
| 目标 | 确认当前分支、未提交文件、构建状态和测试状态。 |
| 产出物 | Git 状态记录、构建日志、基线结论。 |

建议命令：

```bash
git status --short

export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew testDebugUnitTest assembleDebug > /tmp/nbn-b0-baseline.log 2>&1
tail -n 30 /tmp/nbn-b0-baseline.log
```

具体任务：

| 任务 | 可验证通过条件 |
| --- | --- |
| 检查当前分支和 Git 状态 | 明确当前分支、未跟踪文件、已修改文件，避免覆盖 A/C 或其他人的内容。 |
| 确认 JDK 和 Android SDK 环境 | `JAVA_HOME` 指向 JDK 21，`ANDROID_HOME` 和 `ANDROID_SDK_ROOT` 已设置。 |
| 运行单测和构建 | `testDebugUnitTest assembleDebug` 成功，或失败原因和日志路径明确。 |
| 保存失败摘要 | 如果失败，记录 `/tmp/nbn-b0-baseline.log` 和最后 30 行摘要。 |

通过条件：

- 当前 Git 状态明确。
- 构建成功，或失败原因和日志路径明确。
- 如果构建失败，只修构建阻塞，不进入 B1/B2。

## 4. B0.2 B 负责目录审计

| 项目 | 内容 |
| --- | --- |
| 负责人 | B |
| 目标 | 明确 B 负责目录中已有代码、可复用点、缺口和后续改造范围。 |
| 产出物 | 保留、改造、新增、待确认清单。 |

审计范围：

```text
app/src/main/java/com/nbn/adfeed/data/
app/src/main/java/com/nbn/adfeed/ai/
app/src/main/java/com/nbn/adfeed/analytics/
app/src/test/
docs/api-contract.md
docs/technical-design.md
docs/team-plan.md
```

建议命令：

```bash
rg --files app/src/main/java/com/nbn/adfeed/data
rg --files app/src/main/java/com/nbn/adfeed/ai
rg --files app/src/main/java/com/nbn/adfeed/analytics
rg --files app/src/test docs
```

审计结论模板：

| 模块 | 当前状态 | B0 判断 |
| --- | --- | --- |
| `data.model` | 现有 `AdItem`、`AdContentType`、`InteractionState` | 可保留方向，但字段不足，需要补 `description`、`imageUrl`、`videoUrl`、`stats`、`contentHash`。 |
| `data.repository` | 现有 `AdRepository` 只有同步列表方法 | 必须改造，无法支撑分页、错误态、详情、状态同步。 |
| `data.mock` | 现有 `MockAdRepository` 只有少量硬编码数据 | 必须升级为 Mock JSON + 分页数据源。 |
| `data.remote` | 目前只有包职责说明 | 需要新增 Remote 数据源接口和失败降级边界。 |
| `ai` | 摘要/标签接口是占位 | 必须新增缓存、来源标记、规则降级、输出约束。 |
| `analytics` | 有简单计数占位 | 需要和 C 对齐事件模型。 |
| `test` | 测试覆盖很弱 | 必须新增数据、AI、状态、降级测试。 |

通过条件：

- 能说清楚每个目录保留什么、改造什么、新增什么。
- 能指出当前 B 最大缺口：Repository 契约、Mock 数据、状态一致性、AI 降级、测试。

## 5. A0：需要 A 给 B 的输入

| 项目 | 内容 |
| --- | --- |
| 负责人 | A 提供，B 收集 |
| 目标 | 确认 Feed 和详情页对 B 的字段、状态、错误态依赖。 |
| 如果 A 暂时没给 | B 先按默认方案推进，并标记为待 A 确认。 |

A 需要确认的内容：

| A 的需求 | B 要确认的内容 | 默认方案 |
| --- | --- | --- |
| Feed 卡片字段 | 标题、品牌、摘要、图片、视频、标签、互动状态 | `AdItem` 全部提供。 |
| 三类卡片区分 | 大图、小图、视频 | 使用 `AdContentType`。 |
| 详情页字段 | 描述、图片、视频、统计、AI 摘要标签 | `AdItem` 覆盖详情展示字段。 |
| 刷新和加载更多 | 首屏、刷新、下一页 | 使用 `PageRequest` 和 `PageResult`。 |
| 错误态和空态 | 空频道、无搜索结果、加载失败 | 使用 `DataResult`。 |
| 点赞收藏状态 | UI 维护还是数据层维护 | Repository 是单一状态源。 |

A0 通过条件：

- A 不需要直接读 Mock。
- A 确认 Feed 和详情所需字段基本够用。
- 如果字段不确定，B 先按“Feed + Detail + AI + Stats”全集字段设计。

## 6. C0：需要 C 给 B 的输入

| 项目 | 内容 |
| --- | --- |
| 负责人 | C 提供，B 收集 |
| 目标 | 确认搜索、标签过滤、统计事件对 B 的数据和接口依赖。 |
| 如果 C 暂时没给 | B 先按默认搜索和统计字段推进。 |

C 需要确认的内容：

| C 的需求 | B 要确认的内容 | 默认方案 |
| --- | --- | --- |
| 搜索匹配字段 | 标题、品牌、描述、标签、频道 | 全部支持。 |
| 标签筛选方式 | 单标签、多标签、频道内筛选 | 先支持单标签 + 频道。 |
| 统计事件类型 | 曝光、点击、点赞、收藏、分享、搜索 | 先定义统一 `AnalyticsEvent`。 |
| 事件字段 | `eventType`、`adId`、`channel`、`timestamp`、`sourcePage` | 全部提供。 |
| 搜索结果分页 | 是否分页 | 复用 `PageRequest` 和 `PageResult`。 |
| 搜索结果状态同步 | 是否与 Feed/详情一致 | Repository 单一状态源。 |

C0 通过条件：

- C 不复制广告数据。
- C 的搜索和统计都能关联 `adId`。
- C 确认最小搜索能力：标题、品牌、标签可命中。

## 7. B0.3 输出保留 / 改造 / 新增清单

| 项目 | 内容 |
| --- | --- |
| 负责人 | B |
| 目标 | 把 B1/B2/B3 的改造范围明确下来，避免边写边猜。 |
| 产出物 | 保留清单、改造清单、新增清单、A/C 待确认清单。 |

建议结论：

| 类型 | 内容 |
| --- | --- |
| 保留 | `AdContentType`、`InteractionState` 的基本方向、`AdRepository` 的统一数据入口思想、`AiSummaryService` 的接口方向。 |
| 改造 | `AdItem` 字段、`AdRepository` 方法、`MockAdRepository` 数据来源、AI 服务返回结构。 |
| 新增 | `PageRequest`、`PageResult`、`DataResult`、`AdStats`、`SearchRequest`、`AiResponse`、`AiCache`、Mock JSON、Repository 测试。 |
| 待 A 确认 | 卡片字段、详情字段、错误态展示字段。 |
| 待 C 确认 | 搜索过滤字段、统计事件字段、曝光口径。 |

通过条件：

- 后续 B1/B2 要做什么非常明确。
- 没有“边写边猜 A/C 需求”的风险。

## 8. B0.4 输出 B1/B2 前置契约草案

| 项目 | 内容 |
| --- | --- |
| 负责人 | B |
| 目标 | 在正式编码前，先形成可给 A/C 讨论的模型和接口草案。 |
| 产出物 | 模型草案、Repository 草案、AI 返回草案。 |

建议模型草案：

```text
AdItem
AdStats
InteractionState
PageRequest
PageResult<T>
DataResult<T>
SearchRequest
AiResponse<T>
InteractionAction
AnalyticsEvent
```

建议 Repository 草案：

```java
DataResult<PageResult<AdItem>> loadAds(PageRequest request);
DataResult<AdItem> getAdById(String adId);
DataResult<PageResult<AdItem>> searchAds(SearchRequest request);
DataResult<AdItem> updateInteraction(String adId, InteractionAction action);
AiResponse<String> getAiSummary(String adId);
AiResponse<List<String>> getAiTags(String adId);
```

通过条件：

- A 能看懂怎么拿 Feed 和详情。
- C 能看懂怎么搜索和记录统计。
- B 能进入 B1 正式补模型，进入 B2 正式冻结接口。

## 9. B0.5 B0 验收门槛

| 验收项 | 通过条件 |
| --- | --- |
| 构建状态 | `testDebugUnitTest assembleDebug` 成功，或失败原因明确。 |
| 目录审计 | `data`、`ai`、`analytics`、`test` 缺口明确。 |
| A 依赖 | Feed/详情字段需求已确认，或有默认方案并标记待确认。 |
| C 依赖 | 搜索/统计字段需求已确认，或有默认方案并标记待确认。 |
| 改造清单 | 保留、改造、新增清单明确。 |
| B1/B2 输入 | 数据模型草案和 Repository 草案明确。 |
| 风险 | P0/P1/P2 初步风险明确。 |

B0 不能通过的情况：

- 构建失败且原因不明。
- A/C 依赖完全不清楚，且没有默认方案。
- Repository 的下一步接口方向不明确。
- B 不知道哪些文件要保留、哪些要改造、哪些要新增。

## 10. B0 完成后的下一步

B0 完成后，按这个顺序继续：

```text
B1 数据模型补齐
-> B2 Repository v1 冻结
-> B3 Mock JSON 和数据覆盖矩阵
-> B4 分页/详情/搜索实现
-> B5 状态一致性
-> B6 AI 摘要标签缓存降级
```

团队并行顺序：

```text
B0 完成
-> A0/C0 确认字段和事件依赖
-> B1/B2 冻结接口
-> A1/C1 基于接口并行开发
-> B3/B4 提供真实 Mock 数据和分页搜索
-> A2/C2 联调刷新、详情、搜索、统计
```

当前最优先动作：

```text
执行 B0.1 和 B0.2，然后整理一份 B0 基线审计结果。
```
