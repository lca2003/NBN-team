package com.nbn.adfeed.ai;

import com.nbn.adfeed.data.remote.RemoteAdException;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class BackendAdAiServiceTest {
    @Test
    public void summaryAndTagsUseBackendAiRoutes() {
        RecordingTransport transport = new RecordingTransport(
                "{\"requestId\":\"req-test\",\"code\":\"OK\",\"message\":\"ok\",\"data\":{"
                        + "\"adId\":\"ad_001\",\"summary\":\"后端 AI 摘要\",\"source\":\"remote_ai\",\"fallbackReason\":\"\"}}",
                "{\"requestId\":\"req-test\",\"code\":\"OK\",\"message\":\"ok\",\"data\":{"
                        + "\"adId\":\"ad_001\",\"tags\":[\"后端标签\",\"学生党\",\"通勤\"],\"source\":\"remote_ai\"}}"
        );
        BackendAdAiService service = new BackendAdAiService(transport, null);

        AiResponse<String> summary = service.getAiSummary("ad_001");
        AiResponse<List<String>> tags = service.getAiTags("ad_001");

        assertEquals("后端 AI 摘要", summary.getValue());
        assertEquals(AiOutputSource.REMOTE_AI, summary.getSource());
        assertFalse(summary.isCached());
        assertEquals(List.of("后端标签", "学生党", "通勤"), tags.getValue());
        assertEquals(AiOutputSource.REMOTE_AI, tags.getSource());
        assertEquals("/v1/ai/ads/ad_001/summary", transport.summaryPath);
        assertEquals("/v1/ai/ads/ad_001/tags", transport.tagsPath);
    }

    @Test
    public void backendFailureFallsBackToLocalAiService() {
        BackendAdAiService service = new BackendAdAiService(
                path -> {
                    throw new RemoteAdException(RemoteAdException.Reason.NETWORK, "offline");
                },
                new FixedFallbackAiService()
        );

        AiResponse<String> summary = service.getAiSummary("ad_001");
        AiResponse<List<String>> tags = service.getAiTags("ad_001");

        assertEquals("本地摘要", summary.getValue());
        assertEquals(AiOutputSource.MOCK_FALLBACK, summary.getSource());
        assertEquals(List.of("本地标签", "兜底", "可用"), tags.getValue());
        assertEquals(AiOutputSource.MOCK_FALLBACK, tags.getSource());
    }

    private static final class RecordingTransport implements BackendAdAiService.Transport {
        private final String summaryResponse;
        private final String tagsResponse;
        private String summaryPath;
        private String tagsPath;

        private RecordingTransport(String summaryResponse, String tagsResponse) {
            this.summaryResponse = summaryResponse;
            this.tagsResponse = tagsResponse;
        }

        @Override
        public String post(String path) {
            if (path.endsWith("/summary")) {
                summaryPath = path;
                return summaryResponse;
            }
            tagsPath = path;
            return tagsResponse;
        }
    }

    private static final class FixedFallbackAiService implements AdAiService {
        @Override
        public AiResponse<String> getAiSummary(String adId) {
            return AiResponse.success("本地摘要", AiOutputSource.MOCK_FALLBACK, false);
        }

        @Override
        public AiResponse<List<String>> getAiTags(String adId) {
            return AiResponse.success(List.of("本地标签", "兜底", "可用"), AiOutputSource.MOCK_FALLBACK, false);
        }
    }
}
