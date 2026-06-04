package com.nbn.adfeed.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=not-configured")
class AdFeedBackendApplicationTest {
    @Test
    void contextLoadsWithoutRealDashScopeKey() {
    }
}
