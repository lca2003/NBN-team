# AGENTS.md

本文件约束本仓库内的自动化代理、AI 助手和团队成员协作方式。

## 防断流大输出模式

当任务可能产生大量内容时，必须避免在聊天中一次性输出长文本。

1. 大段内容写入本地文件，不直接贴在对话里。
2. HTML、Markdown、代码生成、报告、学习资料等超过约 500 行时，必须落盘为文件。
3. 运行可能产生大量输出的命令时，使用重定向，例如 `> /tmp/task.log`，只展示 `tail -n 20` 或摘要。
4. 不要用 `cat` 打印大文件；改用 `wc -l`、`rg`、`head`、`tail`、定点 `sed -n` 做检查。
5. 工具输出要限制体量，避免一次性返回过多内容。
6. 中间进度只用 1-2 句话说明当前阶段，不展开正文。
7. 最终回答只包含：生成文件路径、覆盖/校验结果、是否有失败项、下一步建议。
8. 如果用户要求输出完整正文，先询问是否改为分块输出或写入本地文件。

## 工程规则

- Android 版本为本仓库唯一实现方向，不新增 iOS 或 Web App。
- Java 版本固定为 21；提交前必须确认构建命令使用 JDK 21。
- 不提交密钥、Token、签名证书、个人本地路径。
- 不直接删除或重写他人模块；跨模块改动需要在提交说明里解释原因。
- 新功能优先补齐接口契约或文档，再写实现。
- Mock 数据需要可替换为网络数据，不能把 UI 与数据源硬耦合。

## 目录职责

- `app/src/main/java/com/nbn/adfeed/data/`：广告模型、Repository、Mock/Remote 数据源。
- `app/src/main/java/com/nbn/adfeed/ai/`：摘要、标签、对话式搜索、AI 降级方案。
- `app/src/main/java/com/nbn/adfeed/analytics/`：曝光、点击、互动统计口径。
- `app/src/main/java/com/nbn/adfeed/video/`：视频播放和资源复用。
- `docs/`：技术方案、接口协议、分工排期、演示脚本。
- `reports/`：课题原始资料提取、调研或验证报告。

## 验证命令

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew assembleDebug
```

提交前至少运行一次 `./gradlew assembleDebug`。如果构建失败，先记录失败日志路径和最后 30 行摘要，再修复。

## Git 协作

- 正确认知 `dev` 分支：`dev` 不是每个人直接写代码的地方，而是功能完成后统一合并、联调和验证的地方。
- `main` 是稳定交付分支，只放最终可演示、可构建的版本。
- `dev` 是集成分支，用于汇总三个人完成后的功能。
- `feature/*` 是个人或模块开发分支，日常开发应在这里完成。
- 分支结构约定：`main` -> `dev` -> `feature/feed`、`feature/data-ai`、`feature/search-stats`。
- 队长初始化 `dev` 分支：

```bash
git checkout main
git pull origin main
git checkout -b dev
git push -u origin dev
```

- 每个人从 `dev` 拉自己的功能分支：

```bash
git checkout dev
git pull origin dev
git checkout -b feature/feed
```

- 三人分支建议：`feature/feed`、`feature/data-ai`、`feature/search-stats`。
- 不要直接向 `main` 提交代码。
- 尽量不要直接在 `dev` 上开发；功能做好后再合并到 `dev`。
- 每天开始开发前先从 `dev` 同步一次，减少冲突。
- 合并到 `dev` 前必须确认本地能通过 `./gradlew assembleDebug`。
- `README.md`、`AGENTS.md`、`docs/` 属于公共文件，修改前最好先在小组里同步，避免多人同时修改造成冲突。
- 提交信息格式：`type(scope): summary`，例如 `feat(feed): add channel tabs`。
- 推荐类型：`feat`、`fix`、`docs`、`refactor`、`test`、`chore`。
- PR 或合并前说明：改动范围、验证命令、已知问题。
