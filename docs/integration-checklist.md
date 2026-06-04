# 集成验收清单

## 自动验证

- `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`
- `export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools`
- `export ANDROID_SDK_ROOT=$ANDROID_HOME`
- `./gradlew testDebugUnitTest > /tmp/nbn-b1-b6-tests.log 2>&1`
- `./gradlew testDebugUnitTest assembleDebug > /tmp/nbn-final.log 2>&1`

## 真机或模拟器走查命令

- `$ANDROID_HOME/platform-tools/adb devices -l`
- `$ANDROID_HOME/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk`
- `$ANDROID_HOME/platform-tools/adb shell am start -n com.nbn.adfeed/.MainActivity`
- 走查结果写入 `docs/stage-completion-audit.md`，大日志写入 `/tmp/nbn-device-walkthrough.log`。

## Feed

- 首屏展示广告列表。
- 频道可切换：精选、电商、本地。
- 刷新回到第一页，不追加旧数据。
- 加载更多使用 `nextCursor`，无更多时提示。
- 大图、小图、视频三类广告可区分。
- 加载态、空态、错误态由 `FeedScreenFormatter` 根据 `DataResult` 触发。
- Feed 不直接读取 Mock JSON。

## 详情

- 点击 Feed 卡片进入详情。
- 详情通过 `getAdById(adId)` 读取。
- 展示描述、图片 URL、视频 URL、统计、AI 摘要、AI 标签。
- 返回 Feed 后保留列表位置。

## 状态一致性

- Feed 点赞后进入详情仍为已点赞。
- 详情收藏后返回 Feed 仍为已收藏。
- 搜索结果读取同一 Repository 状态。
- 刷新后互动状态不丢。
- 统计事件失败不影响互动状态。

## 搜索与标签

- 对话式搜索结果来自 `searchAds(SearchRequest)`。
- 标题、品牌、描述、标签可命中。
- 点击 Feed 标签进入 `#标签` 搜索。
- 固定关键词：学生党、运动、咖啡、数码、通勤。

## 统计

- 事件字段包含 `eventType`、`adId`、`channelId`、`timestamp`、`sourcePage`。
- Feed 曝光、点击、点赞、收藏、分享可记录。
- 搜索事件可记录。
- 搜索页输入 `统计` 可查看内存统计摘要。

## AI

- 每条广告能获取摘要和标签。
- 空描述广告不崩溃。
- 第二次请求同一广告命中缓存。
- 内容变化后缓存失效。
- 默认路径可离线演示 demo remote，不依赖密钥或网络。
- Remote AI 失败走 Mock 或规则降级。
- AI 来源可区分：`remote_ai`、`cache`、`rule_fallback`、`mock_fallback`。

## 视频

- 同一时间只允许一个 active video。
- 切换新视频时旧视频进入 paused。
- 暂停、停止、滚出释放保留播放位置。
- 默认静音，可切换。
- 无效 adId 不污染 active 状态。

## 当前已知限制

- 真实网络 AI 或本地模型服务未接入；当前为离线 demo remote，可替换为真实服务。
- Feed 已有本地媒体兜底卡片，视频复用状态机已可验证；详情页仍需继续增强真实媒体区域和播放组件。
- 统计为内存态，App 重启后清空。
- 真机 `CDY_AN90` 已完成 UI 走查；截图和 UI dump 保存在 `/tmp/nbn-*.png`、`/tmp/nbn-*.xml`，日志保存在 `/tmp/nbn-device-walkthrough.log`。
