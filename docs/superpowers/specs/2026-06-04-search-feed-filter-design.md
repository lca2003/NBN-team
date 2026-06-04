# 对话式搜索联动信息流设计

## 背景

搜索浮层已经通过 `OnSearchResultListener` 将 `matchedAdIds` 回传给宿主 Activity，但 `MainActivity.onSearchResult` 目前尚未把结果同步到信息流。信息流已有频道、标签和分页刷新状态，标签过滤通过 `currentTagFilter` 与 `AdCatalog.loadPage` 实现。

## 目标

对话式搜索返回匹配广告 ID 后，首页信息流展示当前频道内命中的广告。搜索结果覆盖当前标签过滤；切换频道时清空搜索结果与标签过滤。

## 方案选择

采用“搜索覆盖标签、保留当前频道”的方案。

备选方案一是全局搜索覆盖频道和标签，演示时更直接，但会绕开当前频道状态。备选方案二是搜索、频道、标签全部叠加，表达最精确，但用户容易得到空列表，且需要额外解释多重过滤条件。

推荐方案改动小，符合当前 `FeedFragment` 由频道驱动刷新的结构，也能和已有标签过滤自然共存。

## 架构

`MainActivity` 只负责接收搜索结果并转发给 `FeedFragment`，必要时切回首页。搜索浮层不直接依赖 Feed 或 Adapter。

`FeedFragment` 新增搜索 ID 过滤状态，并提供公开方法接收搜索结果。该方法清空当前标签过滤、重置分页，然后复用现有刷新流程。

`AdCatalog` 在按频道取得种子数据后，按 `matchedAdIds` 过滤广告，再执行标签过滤。过滤应按广告原始顺序输出，避免破坏当前信息流排序。

## 数据流

搜索浮层得到 `matchedAdIds` 后调用宿主 `onSearchResult`。

`MainActivity.onSearchResult` 调用 `feedFragment.applySearchResult(matchedAdIds)` 并展示首页。

`FeedFragment.applySearchResult` 保存搜索 ID，清空标签过滤，调用 `refreshChannel(currentChannel, true)`。

`AdCatalog.loadPage` 使用当前频道、搜索 ID、标签过滤生成第一页和后续页。

## 边界与错误处理

空或 `null` 的 `matchedAdIds` 表示没有可展示结果，信息流清空搜索状态并回到当前频道的普通列表；无结果提示仍由搜索浮层负责。

切换频道时清空搜索过滤和标签过滤，避免用户在新频道中看见旧查询影响。

如果搜索 ID 中包含不存在的广告 ID，忽略这些 ID。全部无效时返回空页，走现有空态。

统计页的搜索回调保持不联动信息流，避免统计页隐式修改首页状态。

## 测试

为广告 ID 过滤补单元测试，覆盖空 ID 返回原列表、按 ID 过滤、忽略不存在 ID，并保持信息流原始顺序。

更新或新增 `AdCatalog` 相关测试时，重点覆盖“频道 + 搜索 ID”与“搜索覆盖标签”的组合行为。最后运行项目既有测试和 `assembleDebug`。
