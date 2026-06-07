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
app/
  src/main/java/com/nbn/adfeed/
    ai/                  AI 摘要、标签、搜索接口与降级实现
    analytics/           曝光、点击、互动统计
    data/model/          广告数据模型与互动状态
    data/mock/           本地 Mock 数据
    data/repository/     数据访问接口
    video/               视频播放资源管理
    MainActivity.java    当前最小启动入口
docs/
  api-contract.md        广告、AI、统计接口契约
  demo-script.md         演示视频脚本
  team-plan.md           三人分工与排期
  technical-design.md    技术方案设计
reports/
  课题文档提取材料
```

## 当前已完成

- Android Java 21 初始工程骨架
- 应用最小启动页
- Mock 广告数据模型
- AI 摘要/标签接口占位与 Mock 实现
- 曝光点击统计占位
- 视频播放管理占位
- README、AGENTS、技术方案、接口契约、团队排期、演示脚本文档

## 后续功能清单

- 单列广告信息流布局与流畅滚动
- 广告卡片多样式：大图、小图、视频
- 顶部频道 Tab 与刷新数据
- 详情页与返回位置保持
- 下拉刷新、上拉加载更多
- 点赞、收藏、分享状态同步
- 视频暂停、续播、静音控制
- 曝光、点击统计与可视化展示
- AI 摘要、智能标签、标签过滤
- 对话式搜索

## AI 声明

本项目允许使用 AI 辅助编码、文档整理、Mock 数据生成与方案比较。所有 AI 生成或辅助生成的功能必须由开发者验证可运行性、边界行为和输出正确性；涉及云端大模型接口时，密钥不得写入仓库或 APK。
