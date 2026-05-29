# DashScope AI Search Backend 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在 `backend` 中创建一个完全独立的 Spring Boot 后端项目，只实现 `POST /api/ai/search`，使用内存广告池、DashScope 优先搜索和本地关键词降级。

**架构：** 后端不加入根 Gradle，不影响 Android `:app`。请求进入 `AiSearchController` 后由 `AiSearchService` 编排：读取 `AdMemoryService` 的内存广告，优先调用 `DashScopeSearchClient`，失败或未配置 key 时调用 `FallbackSearchService`。

**技术栈：** Java 21、Gradle wrapper、Spring Boot 3.5.10、Spring MVC、Spring Validation、Spring AI Alibaba DashScope 1.1.2.3、JUnit Jupiter、MockMvc。

---

## 规格来源

- 规格文档：`docs/superpowers/specs/2026-05-29-dashscope-ai-search-design.md`
- 首版接口：`POST /api/ai/search`
- 首版非目标：不实现摘要接口、标签接口、广告列表接口、统计接口、数据库、Redis、登录鉴权和 Android 网络接入改造。

## 文件结构与职责

### 构建与配置

- 创建：`backend/settings.gradle`
  - 独立 Gradle 项目名。
- 创建：`backend/build.gradle`
  - Spring Boot、Java 21、依赖、测试配置。
- 复制：`gradlew` -> `backend/gradlew`
  - 独立后端 Linux/macOS wrapper。
- 复制：`gradlew.bat` -> `backend/gradlew.bat`
  - 独立后端 Windows wrapper。
- 复制：`gradle/wrapper/gradle-wrapper.jar` -> `backend/gradle/wrapper/gradle-wrapper.jar`
  - 独立后端 wrapper jar。
- 复制：`gradle/wrapper/gradle-wrapper.properties` -> `backend/gradle/wrapper/gradle-wrapper.properties`
  - 独立后端 wrapper 配置。
- 创建：`backend/src/main/resources/application.yml`
  - 端口、应用名、DashScope 配置。

### 应用入口

- 创建：`backend/src/main/java/com/nbn/adfeed/backend/AdFeedBackendApplication.java`
  - Spring Boot 启动类。
- 创建：`backend/src/test/java/com/nbn/adfeed/backend/AdFeedBackendApplicationTest.java`
  - 无真实 DashScope key 时的上下文启动测试。

### 广告内存模型

- 创建：`backend/src/main/java/com/nbn/adfeed/backend/ad/AdItem.java`
  - 内存广告记录。
- 创建：`backend/src/main/java/com/nbn/adfeed/backend/ad/AdMemoryService.java`
  - 启动时构造不少于 6 条广告，提供只读广告列表。
- 创建：`backend/src/test/java/com/nbn/adfeed/backend/ad/AdMemoryServiceTest.java`
  - 验证预置广告数量、频道覆盖、返回列表不可修改。

### AI 搜索契约与编排

- 创建：`backend/src/main/java/com/nbn/adfeed/backend/ai/AiSearchRequest.java`
  - 请求 DTO，包含 `query`、`channels`、`limit`。
- 创建：`backend/src/main/java/com/nbn/adfeed/backend/ai/AiSearchResponse.java`
  - 响应 DTO，包含 `answer`、`matchedAdIds`、`fallback`。
- 创建：`backend/src/main/java/com/nbn/adfeed/backend/ai/AiSearchService.java`
  - 搜索主流程，负责远程优先和降级切换。
- 创建：`backend/src/main/java/com/nbn/adfeed/backend/ai/AiSearchController.java`
  - `POST /api/ai/search` HTTP 入口。
- 创建：`backend/src/test/java/com/nbn/adfeed/backend/ai/AiSearchControllerTest.java`
  - 请求校验和无 key 降级接口测试。
- 创建：`backend/src/test/java/com/nbn/adfeed/backend/ai/AiSearchServiceTest.java`
  - 编排逻辑单元测试。

### DashScope 与降级

- 创建：`backend/src/main/java/com/nbn/adfeed/backend/ai/DashScopeSearchClient.java`
  - 使用 Spring AI `ChatClient` 调用 DashScope，构造提示词，解析和校验 JSON。
- 创建：`backend/src/main/java/com/nbn/adfeed/backend/ai/FallbackSearchService.java`
  - 本地确定性关键词搜索。
- 创建：`backend/src/test/java/com/nbn/adfeed/backend/ai/DashScopeSearchClientTest.java`
  - 不联网测试模型 JSON 解析和结果校验。
- 创建：`backend/src/test/java/com/nbn/adfeed/backend/ai/FallbackSearchServiceTest.java`
  - 本地搜索评分、频道过滤、默认推荐测试。

### 错误响应

- 创建：`backend/src/main/java/com/nbn/adfeed/backend/common/ApiErrorResponse.java`
  - 错误响应 DTO。
- 创建：`backend/src/main/java/com/nbn/adfeed/backend/common/ApiExceptionHandler.java`
  - `400` 和 `500` 的统一错误响应。

---

## 实现任务

### 任务 1：创建独立后端 Gradle 骨架

**文件：**
- 创建：`backend/settings.gradle`
- 创建：`backend/build.gradle`
- 创建：`backend/src/main/resources/application.yml`
- 创建：`backend/src/main/java/com/nbn/adfeed/backend/AdFeedBackendApplication.java`
- 创建：`backend/src/test/java/com/nbn/adfeed/backend/AdFeedBackendApplicationTest.java`
- 复制：`gradlew`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.jar`、`gradle/wrapper/gradle-wrapper.properties` 到 `backend/`

- [ ] **步骤 1：复制 Gradle wrapper 到 `backend`**

运行：

```powershell
New-Item -ItemType Directory -Force -Path backend\gradle\wrapper | Out-Null
Copy-Item -LiteralPath gradlew -Destination backend\gradlew
Copy-Item -LiteralPath gradlew.bat -Destination backend\gradlew.bat
Copy-Item -LiteralPath gradle\wrapper\gradle-wrapper.jar -Destination backend\gradle\wrapper\gradle-wrapper.jar
Copy-Item -LiteralPath gradle\wrapper\gradle-wrapper.properties -Destination backend\gradle\wrapper\gradle-wrapper.properties
```

预期：`backend` 拥有自己的 `gradlew`、`gradlew.bat` 和 `gradle/wrapper`。

- [ ] **步骤 2：创建独立 Gradle 配置**

`backend/settings.gradle`：

```groovy
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = 'nbn-adfeed-backend'
```

`backend/build.gradle`：

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.10'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.nbn.adfeed'
version = '0.1.0'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'com.alibaba.cloud.ai:spring-ai-alibaba-starter-dashscope:1.1.2.3'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

`backend/src/main/resources/application.yml`：

```yaml
server:
  port: 8080

spring:
  application:
    name: nbn-adfeed-backend
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY:not-configured}
      chat:
        options:
          model: qwen-plus
```

说明：`not-configured` 是非敏感哨兵值，用于保证无真实 key 时应用仍能启动；业务代码会把它视为未配置并走本地降级。

- [ ] **步骤 3：创建最小启动类**

`backend/src/main/java/com/nbn/adfeed/backend/AdFeedBackendApplication.java`：

```java
package com.nbn.adfeed.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AdFeedBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdFeedBackendApplication.class, args);
    }
}
```

- [ ] **步骤 4：写上下文启动测试**

`backend/src/test/java/com/nbn/adfeed/backend/AdFeedBackendApplicationTest.java`：

```java
package com.nbn.adfeed.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=not-configured")
class AdFeedBackendApplicationTest {
    @Test
    void contextLoadsWithoutRealDashScopeKey() {
    }
}
```

- [ ] **步骤 5：运行测试**

运行：

```powershell
cd backend
.\gradlew.bat test --tests "com.nbn.adfeed.backend.AdFeedBackendApplicationTest"
```

预期：如果本机 `JAVA_HOME` 未指向 JDK 21，Gradle 会明确报 Java toolchain 问题；设置 JDK 21 后测试通过。

- [ ] **步骤 6：Commit**

```bash
git add backend
git commit -m "chore(backend): scaffold spring boot project"
```

---

### 任务 2：实现内存广告池

**文件：**
- 创建：`backend/src/main/java/com/nbn/adfeed/backend/ad/AdItem.java`
- 创建：`backend/src/main/java/com/nbn/adfeed/backend/ad/AdMemoryService.java`
- 创建：`backend/src/test/java/com/nbn/adfeed/backend/ad/AdMemoryServiceTest.java`

- [ ] **步骤 1：编写失败测试**

`backend/src/test/java/com/nbn/adfeed/backend/ad/AdMemoryServiceTest.java`：

```java
package com.nbn.adfeed.backend.ad;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdMemoryServiceTest {
    private final AdMemoryService service = new AdMemoryService();

    @Test
    void providesAtLeastSixAdsAcrossRequiredChannels() {
        assertThat(service.findAll()).hasSizeGreaterThanOrEqualTo(6);
        assertThat(service.findAll())
                .extracting(AdItem::channel)
                .contains("精选", "电商", "本地");
    }

    @Test
    void returnsReadOnlyAds() {
        assertThatThrownBy(() -> service.findAll().add(new AdItem(
                "x", "title", "brand", "精选", "description", java.util.List.of("tag")
        ))).isInstanceOf(UnsupportedOperationException.class);
    }
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```powershell
cd backend
.\gradlew.bat test --tests "com.nbn.adfeed.backend.ad.AdMemoryServiceTest"
```

预期：编译失败，提示 `AdMemoryService` 或 `AdItem` 不存在。

- [ ] **步骤 3：实现广告记录和内存广告池**

`backend/src/main/java/com/nbn/adfeed/backend/ad/AdItem.java`：

```java
package com.nbn.adfeed.backend.ad;

import java.util.List;

public record AdItem(
        String id,
        String title,
        String brand,
        String channel,
        String description,
        List<String> tags
) {
}
```

`backend/src/main/java/com/nbn/adfeed/backend/ad/AdMemoryService.java`：

```java
package com.nbn.adfeed.backend.ad;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdMemoryService {
    private final List<AdItem> ads = List.of(
            new AdItem("ad_001", "轻量跑鞋新品首发", "NBN Sports", "精选",
                    "适合通勤和夜跑的轻量缓震跑鞋，学生党也容易入手。",
                    List.of("运动", "通勤", "学生党", "性价比")),
            new AdItem("ad_002", "城市露营便携灯", "NBN Outdoor", "精选",
                    "小体积高亮度，适合周末露营、夜市摆摊和宿舍应急。",
                    List.of("露营", "便携", "夜间", "户外")),
            new AdItem("ad_003", "宿舍桌面收纳套装", "NBN Home", "电商",
                    "组合式收纳盒，适合书桌、化妆台和宿舍小空间整理。",
                    List.of("收纳", "宿舍", "学生党", "桌面")),
            new AdItem("ad_004", "平价蓝牙耳机限时活动", "NBN Audio", "电商",
                    "低延迟蓝牙耳机，适合通勤听歌、网课和轻度游戏。",
                    List.of("耳机", "通勤", "网课", "平价")),
            new AdItem("ad_005", "周边健身房体验课", "NBN Fitness", "本地",
                    "本地门店体验课，覆盖力量训练、减脂课程和新手指导。",
                    List.of("健身", "本地", "体验课", "运动")),
            new AdItem("ad_006", "校园打印优惠套餐", "NBN Print", "本地",
                    "面向学生的资料打印、论文装订和证件照优惠服务。",
                    List.of("校园", "打印", "学生党", "优惠"))
    );

    public List<AdItem> findAll() {
        return ads;
    }
}
```

- [ ] **步骤 4：运行测试确认通过**

运行：

```powershell
cd backend
.\gradlew.bat test --tests "com.nbn.adfeed.backend.ad.AdMemoryServiceTest"
```

预期：测试通过。

- [ ] **步骤 5：Commit**

```bash
git add backend/src/main/java/com/nbn/adfeed/backend/ad backend/src/test/java/com/nbn/adfeed/backend/ad
git commit -m "feat(backend): add in-memory ad pool"
```

---

### 任务 3：实现本地降级搜索

**文件：**
- 创建：`backend/src/main/java/com/nbn/adfeed/backend/ai/AiSearchRequest.java`
- 创建：`backend/src/main/java/com/nbn/adfeed/backend/ai/AiSearchResponse.java`
- 创建：`backend/src/main/java/com/nbn/adfeed/backend/ai/FallbackSearchService.java`
- 创建：`backend/src/test/java/com/nbn/adfeed/backend/ai/FallbackSearchServiceTest.java`

- [ ] **步骤 1：编写失败测试**

`backend/src/test/java/com/nbn/adfeed/backend/ai/FallbackSearchServiceTest.java`：

```java
package com.nbn.adfeed.backend.ai;

import com.nbn.adfeed.backend.ad.AdItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackSearchServiceTest {
    private final FallbackSearchService service = new FallbackSearchService();

    private final List<AdItem> ads = List.of(
            new AdItem("ad_001", "轻量跑鞋新品首发", "NBN Sports", "精选",
                    "适合通勤和夜跑的轻量缓震跑鞋，学生党也容易入手。",
                    List.of("运动", "通勤", "学生党", "性价比")),
            new AdItem("ad_002", "宿舍桌面收纳套装", "NBN Home", "电商",
                    "适合宿舍小空间整理。",
                    List.of("收纳", "宿舍", "学生党")),
            new AdItem("ad_003", "周边健身房体验课", "NBN Fitness", "本地",
                    "本地门店体验课，覆盖力量训练和新手指导。",
                    List.of("健身", "本地", "运动"))
    );

    @Test
    void matchesQueryByTagsAndDescription() {
        AiSearchResponse response = service.search(new AiSearchRequest(
                "学生党 运动", List.of(), 10
        ), ads);

        assertThat(response.fallback()).isTrue();
        assertThat(response.matchedAdIds()).containsExactly("ad_001");
    }

    @Test
    void filtersByChannelsBeforeScoring() {
        AiSearchResponse response = service.search(new AiSearchRequest(
                "运动", List.of("本地"), 10
        ), ads);

        assertThat(response.matchedAdIds()).containsExactly("ad_003");
    }

    @Test
    void returnsDefaultRecommendationsWhenNothingMatches() {
        AiSearchResponse response = service.search(new AiSearchRequest(
                "咖啡", List.of(), 2
        ), ads);

        assertThat(response.matchedAdIds()).containsExactly("ad_001", "ad_002");
        assertThat(response.answer()).contains("默认推荐");
    }
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```powershell
cd backend
.\gradlew.bat test --tests "com.nbn.adfeed.backend.ai.FallbackSearchServiceTest"
```

预期：编译失败，提示 `FallbackSearchService`、`AiSearchRequest` 或 `AiSearchResponse` 不存在。

- [ ] **步骤 3：实现请求和响应 DTO**

`backend/src/main/java/com/nbn/adfeed/backend/ai/AiSearchRequest.java`：

```java
package com.nbn.adfeed.backend.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AiSearchRequest(
        @NotBlank(message = "query must not be blank")
        @Size(max = 200, message = "query length must be at most 200")
        String query,
        List<String> channels,
        @Min(value = 1, message = "limit must be at least 1")
        @Max(value = 20, message = "limit must be at most 20")
        Integer limit
) {
    public List<String> safeChannels() {
        return channels == null ? List.of() : channels;
    }

    public int safeLimit() {
        return limit == null ? 10 : limit;
    }
}
```

`backend/src/main/java/com/nbn/adfeed/backend/ai/AiSearchResponse.java`：

```java
package com.nbn.adfeed.backend.ai;

import java.util.List;

public record AiSearchResponse(
        String answer,
        List<String> matchedAdIds,
        boolean fallback
) {
}
```

- [ ] **步骤 4：实现降级搜索服务**

`backend/src/main/java/com/nbn/adfeed/backend/ai/FallbackSearchService.java`：

```java
package com.nbn.adfeed.backend.ai;

import com.nbn.adfeed.backend.ad.AdItem;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;

@Service
public class FallbackSearchService {
    public AiSearchResponse search(AiSearchRequest request, List<AdItem> ads) {
        List<AdItem> candidates = filterByChannels(request, ads);
        if (candidates.isEmpty()) {
            return new AiSearchResponse("没有找到符合频道范围的广告。", List.of(), true);
        }

        String query = normalize(request.query());
        List<ScoredAd> scoredAds = IntStream.range(0, candidates.size())
                .mapToObj(index -> new ScoredAd(candidates.get(index), score(query, candidates.get(index)), index))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingInt(ScoredAd::score).reversed()
                        .thenComparingInt(ScoredAd::index))
                .toList();

        List<String> matchedIds;
        String answer;
        if (scoredAds.isEmpty()) {
            matchedIds = candidates.stream()
                    .limit(request.safeLimit())
                    .map(AdItem::id)
                    .toList();
            answer = "没有精确命中关键词，已为你返回默认推荐。";
        } else {
            matchedIds = scoredAds.stream()
                    .limit(request.safeLimit())
                    .map(scored -> scored.ad().id())
                    .toList();
            answer = "已根据关键词为你找到相关广告。";
        }

        return new AiSearchResponse(answer, matchedIds, true);
    }

    private List<AdItem> filterByChannels(AiSearchRequest request, List<AdItem> ads) {
        Set<String> channels = new HashSet<>(request.safeChannels());
        if (channels.isEmpty()) {
            return ads;
        }
        return ads.stream()
                .filter(ad -> channels.contains(ad.channel()))
                .toList();
    }

    private int score(String query, AdItem ad) {
        int score = 0;
        for (String token : tokenize(query)) {
            if (token.isBlank()) {
                continue;
            }
            if (ad.tags().stream().map(this::normalize).anyMatch(tag -> tag.contains(token))) {
                score += 4;
            }
            if (normalize(ad.title()).contains(token)) {
                score += 3;
            }
            if (normalize(ad.brand()).contains(token)) {
                score += 2;
            }
            if (normalize(ad.description()).contains(token)) {
                score += 1;
            }
        }
        return score;
    }

    private List<String> tokenize(String value) {
        return List.of(value.split("\\s+"));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record ScoredAd(AdItem ad, int score, int index) {
    }
}
```

- [ ] **步骤 5：运行测试确认通过**

运行：

```powershell
cd backend
.\gradlew.bat test --tests "com.nbn.adfeed.backend.ai.FallbackSearchServiceTest"
```

预期：测试通过。

- [ ] **步骤 6：Commit**

```bash
git add backend/src/main/java/com/nbn/adfeed/backend/ai backend/src/test/java/com/nbn/adfeed/backend/ai/FallbackSearchServiceTest.java
git commit -m "feat(backend): add fallback ai search"
```

---

### 任务 4：实现 Controller、校验和错误响应

**文件：**
- 创建：`backend/src/main/java/com/nbn/adfeed/backend/ai/AiSearchController.java`
- 创建：`backend/src/main/java/com/nbn/adfeed/backend/ai/AiSearchService.java`
- 创建：`backend/src/main/java/com/nbn/adfeed/backend/common/ApiErrorResponse.java`
- 创建：`backend/src/main/java/com/nbn/adfeed/backend/common/ApiExceptionHandler.java`
- 创建：`backend/src/test/java/com/nbn/adfeed/backend/ai/AiSearchControllerTest.java`

- [ ] **步骤 1：编写失败测试**

`backend/src/test/java/com/nbn/adfeed/backend/ai/AiSearchControllerTest.java`：

```java
package com.nbn.adfeed.backend.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=not-configured")
@AutoConfigureMockMvc
class AiSearchControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsBlankQuery() throws Exception {
        mockMvc.perform(post("/api/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": " ",
                                  "limit": 10
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("query must not be blank")));
    }

    @Test
    void returnsFallbackSearchWhenDashScopeKeyIsNotConfigured() throws Exception {
        mockMvc.perform(post("/api/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "学生党 运动",
                                  "channels": ["精选", "电商", "本地"],
                                  "limit": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.matchedAdIds", hasItem("ad_001")));
    }
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```powershell
cd backend
.\gradlew.bat test --tests "com.nbn.adfeed.backend.ai.AiSearchControllerTest"
```

预期：测试失败，提示 HTTP 入口或主服务不存在。

- [ ] **步骤 3：实现搜索编排服务的降级路径**

`backend/src/main/java/com/nbn/adfeed/backend/ai/AiSearchService.java`：

```java
package com.nbn.adfeed.backend.ai;

import com.nbn.adfeed.backend.ad.AdMemoryService;
import org.springframework.stereotype.Service;

@Service
public class AiSearchService {
    private final AdMemoryService adMemoryService;
    private final FallbackSearchService fallbackSearchService;

    public AiSearchService(AdMemoryService adMemoryService, FallbackSearchService fallbackSearchService) {
        this.adMemoryService = adMemoryService;
        this.fallbackSearchService = fallbackSearchService;
    }

    public AiSearchResponse search(AiSearchRequest request) {
        return fallbackSearchService.search(request, adMemoryService.findAll());
    }
}
```

- [ ] **步骤 4：实现 Controller**

`backend/src/main/java/com/nbn/adfeed/backend/ai/AiSearchController.java`：

```java
package com.nbn.adfeed.backend.ai;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiSearchController {
    private final AiSearchService aiSearchService;

    public AiSearchController(AiSearchService aiSearchService) {
        this.aiSearchService = aiSearchService;
    }

    @PostMapping("/search")
    public AiSearchResponse search(@Valid @RequestBody AiSearchRequest request) {
        return aiSearchService.search(request);
    }
}
```

- [ ] **步骤 5：实现错误响应**

`backend/src/main/java/com/nbn/adfeed/backend/common/ApiErrorResponse.java`：

```java
package com.nbn.adfeed.backend.common;

public record ApiErrorResponse(String message) {
}
```

`backend/src/main/java/com/nbn/adfeed/backend/common/ApiExceptionHandler.java`：

```java
package com.nbn.adfeed.backend.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "request validation failed" : error.getDefaultMessage())
                .orElse("request validation failed");
        return ResponseEntity.badRequest().body(new ApiErrorResponse(message));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> handleStatus(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(new ApiErrorResponse(exception.getReason()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("internal server error"));
    }
}
```

- [ ] **步骤 6：运行测试确认通过**

运行：

```powershell
cd backend
.\gradlew.bat test --tests "com.nbn.adfeed.backend.ai.AiSearchControllerTest"
```

预期：测试通过。

- [ ] **步骤 7：Commit**

```bash
git add backend/src/main/java/com/nbn/adfeed/backend/ai backend/src/main/java/com/nbn/adfeed/backend/common backend/src/test/java/com/nbn/adfeed/backend/ai/AiSearchControllerTest.java
git commit -m "feat(backend): expose ai search endpoint"
```

---

### 任务 5：接入 DashScope 搜索客户端

**文件：**
- 创建：`backend/src/main/java/com/nbn/adfeed/backend/ai/DashScopeSearchClient.java`
- 修改：`backend/src/main/java/com/nbn/adfeed/backend/ai/AiSearchService.java`
- 创建：`backend/src/test/java/com/nbn/adfeed/backend/ai/DashScopeSearchClientTest.java`
- 创建：`backend/src/test/java/com/nbn/adfeed/backend/ai/AiSearchServiceTest.java`

- [ ] **步骤 1：编写 DashScope 响应解析失败测试**

`backend/src/test/java/com/nbn/adfeed/backend/ai/DashScopeSearchClientTest.java`：

```java
package com.nbn.adfeed.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbn.adfeed.backend.ad.AdItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashScopeSearchClientTest {
    private final DashScopeSearchClient client = DashScopeSearchClient.forParsingOnly(new ObjectMapper());

    private final List<AdItem> candidates = List.of(
            new AdItem("ad_001", "轻量跑鞋新品首发", "NBN Sports", "精选",
                    "适合通勤和夜跑的轻量缓震跑鞋。",
                    List.of("运动")),
            new AdItem("ad_002", "宿舍桌面收纳套装", "NBN Home", "电商",
                    "适合宿舍小空间整理。",
                    List.of("收纳"))
    );

    @Test
    void parsesValidJsonResponse() {
        AiSearchResponse response = client.parseModelContent("""
                {
                  "answer": "为你找到运动相关广告。",
                  "matchedAdIds": ["ad_001"]
                }
                """, candidates, 10);

        assertThat(response.fallback()).isFalse();
        assertThat(response.answer()).isEqualTo("为你找到运动相关广告。");
        assertThat(response.matchedAdIds()).containsExactly("ad_001");
    }

    @Test
    void rejectsUnknownAdIds() {
        assertThatThrownBy(() -> client.parseModelContent("""
                {
                  "answer": "为你找到广告。",
                  "matchedAdIds": ["ad_999"]
                }
                """, candidates, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMarkdownWrappedJson() {
        assertThatThrownBy(() -> client.parseModelContent("""
                ```json
                {"answer":"为你找到广告。","matchedAdIds":["ad_001"]}
                ```
                """, candidates, 10)).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```powershell
cd backend
.\gradlew.bat test --tests "com.nbn.adfeed.backend.ai.DashScopeSearchClientTest"
```

预期：编译失败，提示 `DashScopeSearchClient` 不存在。

- [ ] **步骤 3：实现 DashScope 客户端和解析校验**

`backend/src/main/java/com/nbn/adfeed/backend/ai/DashScopeSearchClient.java`：

```java
package com.nbn.adfeed.backend.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbn.adfeed.backend.ad.AdItem;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DashScopeSearchClient {
    private static final String API_KEY_SENTINEL = "not-configured";

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public DashScopeSearchClient(
            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
            ObjectMapper objectMapper,
            @Value("${spring.ai.dashscope.api-key:not-configured}") String apiKey
    ) {
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        this.chatClient = builder == null ? null : builder.build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    private DashScopeSearchClient(ObjectMapper objectMapper) {
        this.chatClient = null;
        this.objectMapper = objectMapper;
        this.apiKey = API_KEY_SENTINEL;
    }

    static DashScopeSearchClient forParsingOnly(ObjectMapper objectMapper) {
        return new DashScopeSearchClient(objectMapper);
    }

    public boolean isConfigured() {
        return chatClient != null && apiKey != null && !apiKey.isBlank() && !API_KEY_SENTINEL.equals(apiKey);
    }

    public AiSearchResponse search(AiSearchRequest request, List<AdItem> candidates) {
        if (!isConfigured()) {
            throw new IllegalStateException("DashScope is not configured");
        }
        String content = chatClient.prompt()
                .system(systemPrompt())
                .user(userPrompt(request, candidates))
                .call()
                .content();
        return parseModelContent(content, candidates, request.safeLimit());
    }

    AiSearchResponse parseModelContent(String content, List<AdItem> candidates, int limit) {
        if (content == null || content.isBlank() || content.trim().startsWith("```")) {
            throw new IllegalArgumentException("model response must be plain JSON");
        }
        ModelResponse parsed;
        try {
            parsed = objectMapper.readValue(content, ModelResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("model response is not valid JSON", exception);
        }
        if (parsed.answer() == null || parsed.answer().isBlank()) {
            throw new IllegalArgumentException("model answer must not be blank");
        }
        if (parsed.matchedAdIds() == null) {
            throw new IllegalArgumentException("model matchedAdIds must not be null");
        }
        if (parsed.matchedAdIds().size() > limit) {
            throw new IllegalArgumentException("model returned too many ids");
        }

        Set<String> candidateIds = new HashSet<>(candidates.stream().map(AdItem::id).toList());
        boolean containsUnknownId = parsed.matchedAdIds().stream().anyMatch(id -> !candidateIds.contains(id));
        if (containsUnknownId) {
            throw new IllegalArgumentException("model returned unknown ad id");
        }

        return new AiSearchResponse(parsed.answer(), parsed.matchedAdIds(), false);
    }

    private String systemPrompt() {
        return """
                你是广告信息流搜索助手。只能基于用户给定的候选广告回答。
                不要编造广告 ID，不要返回候选列表之外的 ID。
                输出必须是纯 JSON，不要使用 Markdown 代码块。
                JSON 格式：{"answer":"不超过80个中文字符","matchedAdIds":["ad_001"]}
                """;
    }

    private String userPrompt(AiSearchRequest request, List<AdItem> candidates) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户查询：").append(request.query()).append("\n");
        builder.append("最多返回 ").append(request.safeLimit()).append(" 个广告 ID。\n");
        builder.append("候选广告：\n");
        for (AdItem ad : candidates) {
            builder.append("- id=").append(ad.id())
                    .append(", title=").append(ad.title())
                    .append(", brand=").append(ad.brand())
                    .append(", channel=").append(ad.channel())
                    .append(", description=").append(ad.description())
                    .append(", tags=").append(String.join("/", ad.tags()))
                    .append("\n");
        }
        return builder.toString();
    }

    private record ModelResponse(String answer, List<String> matchedAdIds) {
    }
}
```

- [ ] **步骤 4：运行 DashScope 解析测试确认通过**

运行：

```powershell
cd backend
.\gradlew.bat test --tests "com.nbn.adfeed.backend.ai.DashScopeSearchClientTest"
```

预期：测试通过，不访问网络。

- [ ] **步骤 5：编写搜索编排测试**

`backend/src/test/java/com/nbn/adfeed/backend/ai/AiSearchServiceTest.java`：

```java
package com.nbn.adfeed.backend.ai;

import com.nbn.adfeed.backend.ad.AdMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiSearchServiceTest {
    @Test
    void fallsBackWhenDashScopeIsNotConfigured() {
        DashScopeSearchClient dashScopeClient = new DashScopeSearchClient(
                emptyProvider(), new com.fasterxml.jackson.databind.ObjectMapper(), "not-configured"
        );
        AiSearchService service = new AiSearchService(
                new AdMemoryService(),
                new FallbackSearchService(),
                dashScopeClient
        );

        AiSearchResponse response = service.search(new AiSearchRequest("学生党 运动", List.of(), 10));

        assertThat(response.fallback()).isTrue();
        assertThat(response.matchedAdIds()).contains("ad_001");
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<ChatClient.Builder> emptyProvider() {
        ObjectProvider<ChatClient.Builder> provider = mock(ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
```

- [ ] **步骤 6：修改主服务使用 DashScope 优先**

`backend/src/main/java/com/nbn/adfeed/backend/ai/AiSearchService.java`：

```java
package com.nbn.adfeed.backend.ai;

import com.nbn.adfeed.backend.ad.AdItem;
import com.nbn.adfeed.backend.ad.AdMemoryService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiSearchService {
    private final AdMemoryService adMemoryService;
    private final FallbackSearchService fallbackSearchService;
    private final DashScopeSearchClient dashScopeSearchClient;

    public AiSearchService(
            AdMemoryService adMemoryService,
            FallbackSearchService fallbackSearchService,
            DashScopeSearchClient dashScopeSearchClient
    ) {
        this.adMemoryService = adMemoryService;
        this.fallbackSearchService = fallbackSearchService;
        this.dashScopeSearchClient = dashScopeSearchClient;
    }

    public AiSearchResponse search(AiSearchRequest request) {
        List<AdItem> ads = adMemoryService.findAll();
        if (!dashScopeSearchClient.isConfigured()) {
            return fallbackSearchService.search(request, ads);
        }
        try {
            return dashScopeSearchClient.search(request, ads);
        } catch (RuntimeException exception) {
            return fallbackSearchService.search(request, ads);
        }
    }
}
```

- [ ] **步骤 7：运行服务和 Controller 测试**

运行：

```powershell
cd backend
.\gradlew.bat test --tests "com.nbn.adfeed.backend.ai.AiSearchServiceTest" --tests "com.nbn.adfeed.backend.ai.AiSearchControllerTest"
```

预期：测试通过。

- [ ] **步骤 8：Commit**

```bash
git add backend/src/main/java/com/nbn/adfeed/backend/ai backend/src/test/java/com/nbn/adfeed/backend/ai
git commit -m "feat(backend): integrate dashscope search client"
```

---

### 任务 6：完整验证与文档收口

**文件：**
- 修改：`backend/README.md`
- 可选修改：`docs/api-contract.md`

- [ ] **步骤 1：创建后端运行说明**

`backend/README.md`：

```markdown
# NBN Ad Feed Backend

独立 Spring Boot 后端项目，首版只提供 `POST /api/ai/search`。

## 环境

- JDK 21
- DashScope API Key 可选

## 测试

```powershell
.\gradlew.bat test
```

## 打包

```powershell
.\gradlew.bat bootJar
```

## 启动

未配置 DashScope API Key 时会使用本地降级搜索：

```powershell
.\gradlew.bat bootRun
```

配置 DashScope API Key：

```powershell
$env:DASHSCOPE_API_KEY="your_key_here"
.\gradlew.bat bootRun
```

## 搜索接口

```http
POST /api/ai/search
Content-Type: application/json
```

```json
{
  "query": "找适合学生党的运动广告",
  "channels": ["精选", "电商", "本地"],
  "limit": 10
}
```
```

- [ ] **步骤 2：检查是否需要更新 API 契约**

运行：

```powershell
rg -n "对话式搜索|/api/ai/search|matchedAdIds|fallback" docs\api-contract.md docs\superpowers\specs\2026-05-29-dashscope-ai-search-design.md
```

预期：如果 `docs/api-contract.md` 已包含相同请求和响应结构，不修改；如果字段名或路径缺失，只补充 `POST /api/ai/search` 的路径说明，不调整其他模块契约。

- [ ] **步骤 3：运行后端完整测试**

运行：

```powershell
cd backend
.\gradlew.bat test
```

预期：全部测试通过，且没有真实 DashScope 网络调用。

- [ ] **步骤 4：运行后端打包**

运行：

```powershell
cd backend
.\gradlew.bat bootJar
```

预期：`backend/build/libs/nbn-adfeed-backend-0.1.0.jar` 生成成功。

- [ ] **步骤 5：运行根工程 Android 构建验证**

在仓库根目录运行。Windows 本机需要先确保 `JAVA_HOME` 指向 JDK 21；如果当前机器只有 Java 17，记录失败日志路径和最后 30 行摘要。

```powershell
.\gradlew.bat assembleDebug
```

预期：构建通过；若因 JDK 或 Android SDK 环境失败，最终说明中明确记录环境失败原因。

- [ ] **步骤 6：最终状态检查**

运行：

```powershell
git status --short
rg -n "DASHSCOPE_API_KEY=|sk-|dashscope.*key" backend docs
```

预期：没有真实密钥；只出现环境变量名或文档占位说明。

- [ ] **步骤 7：Commit**

```bash
git add backend docs
git commit -m "docs(backend): add ai search runbook"
```

---

## 计划自检清单

- [ ] 规格中的独立 `backend` 项目由任务 1 覆盖。
- [ ] Java 21、Spring Boot、Spring AI Alibaba DashScope 由任务 1 覆盖。
- [ ] `POST /api/ai/search` 由任务 4 覆盖。
- [ ] 内存广告池由任务 2 覆盖。
- [ ] DashScope 优先调用由任务 5 覆盖。
- [ ] 未配置 key、调用异常、解析失败时降级由任务 3 和任务 5 覆盖。
- [ ] 响应字段 `answer`、`matchedAdIds`、`fallback` 由任务 3、任务 4、任务 5 覆盖。
- [ ] 请求校验 `query`、`channels`、`limit` 由任务 3 和任务 4 覆盖。
- [ ] 不真实调用 DashScope 的自动化测试策略由任务 5 覆盖。
- [ ] 后端 `test`、`bootJar` 和根工程 `assembleDebug` 验证由任务 6 覆盖。
- [ ] 密钥不落库检查由任务 6 覆盖。

## 执行方式

推荐使用子代理驱动执行：每个任务独立实现并在任务间审查 diff。也可以在当前会话中用 `executing-plans` 批量执行，按任务 1、任务 3、任务 5、任务 6 设置检查点。
