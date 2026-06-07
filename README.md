# NBN Ad Feed

字节跳动工程训练营课题：Android 单列广告信息流 App。

本仓库是三人协作前的初始工程骨架，当前目标是先保证项目可构建、目录职责清晰、文档可追踪。正式功能开发请按 `docs/team-plan.md` 分工推进。

## 技术栈

- 平台：Android
- 语言：Java 21
- 构建：Gradle Wrapper + Android Gradle Plugin
- SDK：compileSdk 36、minSdk 26、targetSdk 36
- 架构方向：单 Activity 起步，后续按 Feed、Data/AI、Search/Analytics 模块演进

## 环境要求

建议使用 Android Studio 或命令行环境，并确保：

- JDK 21
- Android SDK Platform 36
- Android SDK Build Tools 36.0.0
- Android SDK Platform Tools

本机命令行示例：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew assembleDebug

```
后端启动：
```bash
.\gradlew.bat :backend:runAiBackend8081
.\gradlew.bat :backend:runBackend8080
```

## 项目结构

```text
app/                       Android 客户端主模块
  ai/                      AI 摘要、标签、搜索与降级
  analytics/               曝光、点击、互动统计
  data/                    广告模型、Mock 数据、Repository、远程数据源
  ui/feed/                 首页信息流与广告卡片交互
  ui/detail/               广告详情页
  ui/search/               AI 对话式搜索页面
  ui/stats/                统计数据展示页面
  ui/media/                图片、视频封面加载
  video/                   视频播放管理
  res/                     布局、图标、颜色、字符串等资源
  assets/                  本地广告数据与媒体映射

backend/                   后端服务模块
  ad/                      广告数据服务
  ai/                      AI 搜索服务
  domain/                  业务逻辑
  store/                   JSON 种子数据读取
  resources/               配置、Prompt、种子数据

docs/                      项目文档
  technical-design.md      技术方案设计
  api-contract.md          接口契约
  team-plan.md             团队分工与排期
  demo-script.md           演示脚本

reports/                   项目报告与过程材料
README.md                  项目说明
```

## 当前已完成

- Android Java 21 初始工程骨架
- 应用最小启动页
- Mock 广告数据模型
- AI 摘要/标签接口占位与 Mock 实现
- 曝光点击统计占位
- 视频播放管理占位
- README、AGENTS、技术方案、接口契约、团队排期、演示脚本文档
- 单列广告信息流布局与流畅滚动
- 广告卡片多样式：大图、小图、视频
- 顶部频道 Tab 与刷新数据
- 详情页与返回位置保持
- 下拉刷新、上拉加载更多
- 点赞、收藏、分享状态同步
- 点赞爱心特效、一秒内多次点赞连续触发特效
- 左右滑动切换栏目
- 多标签过滤
- 视频暂停、续播、静音控制
- 曝光、点击统计与可视化展示
- AI 摘要、智能标签、标签过滤
- 对话式搜索

## AI 声明

本项目在开发过程中使用了 AI 工具进行部分辅助，但 AI 仅作为开发过程中的参考工具，不作为项目核心功能实现的主要来源。
AI 主要辅助内容包括：
辅助理解训练营课题要求，帮助梳理信息流页面、广告卡片、详情页、搜索和统计等模块的开发思路。
辅助生成部分开发文档、日报内容和技术方案说明，用于提高文档整理效率。
在代码开发过程中，AI 主要用于提供实现思路参考、排查常见报错、优化代码结构建议，例如 RecyclerView 列表拆分、页面交互逻辑整理、UI 细节优化等。
对部分 Mock 文案、广告摘要、标签描述等非核心数据内容进行了辅助生成或润色。
对项目中出现的编译错误、Git 合并冲突、依赖配置问题等进行辅助分析。
项目的主要功能设计、代码选择、模块划分、功能验证和最终实现均由团队成员完成。AI 生成的内容没有直接作为最终结果无验证使用，所有代码和文档内容都经过人工检查、修改和运行验证。
