package com.nbn.adfeed.data.remote;

import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.DataResult;
import com.nbn.adfeed.data.model.InteractionAction;
import com.nbn.adfeed.data.model.PageRequest;
import com.nbn.adfeed.data.model.PageResult;
import com.nbn.adfeed.data.model.SearchRequest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BackendRemoteAdDataSourceTest {
    @Test
    public void loadAdsParsesBackendFeedEnvelope() throws Exception {
        FakeTransport transport = new FakeTransport();
        BackendRemoteAdDataSource dataSource = new BackendRemoteAdDataSource(transport);

        DataResult<PageResult<AdItem>> result = dataSource.loadAds(PageRequest.firstPage("featured", 10));

        assertEquals(DataResult.Status.SUCCESS, result.getStatus());
        assertEquals(BackendRemoteAdDataSource.SOURCE, result.getSource());
        assertEquals(BackendRemoteAdDataSource.SOURCE, result.getData().getDataSource());
        assertEquals(1, result.getData().getItems().size());
        assertEquals("ad_001", result.getData().getItems().get(0).getId());
        assertEquals("Runner Launch", result.getData().getItems().get(0).getTitle());
        assertEquals(320, result.getData().getItems().get(0).getStats().getLikeCount());
        assertEquals("/v1/feed?channel=featured&cursor=page_1&limit=10", transport.paths.get(0));
    }

    @Test
    public void getAdByIdParsesSingleAdEnvelope() throws Exception {
        BackendRemoteAdDataSource dataSource = new BackendRemoteAdDataSource(new FakeTransport());

        DataResult<AdItem> result = dataSource.getAdById("ad_001");

        assertEquals(DataResult.Status.SUCCESS, result.getStatus());
        assertEquals("NBN Sports", result.getData().getBrand());
        assertTrue(result.getData().getTags().contains("sport"));
        assertFalse(result.getData().getInteractionState().isLiked());
    }

    @Test
    public void searchFiltersBackendFeedDataClientSideUntilAiSearchRouteExists() throws Exception {
        BackendRemoteAdDataSource dataSource = new BackendRemoteAdDataSource(new FakeTransport());

        DataResult<PageResult<AdItem>> result = dataSource.searchAds(SearchRequest.keyword("runner"));

        assertEquals(DataResult.Status.SUCCESS, result.getStatus());
        assertEquals(1, result.getData().getItems().size());
        assertEquals("ad_001", result.getData().getItems().get(0).getId());
    }

    @Test
    public void updateInteractionPostsToBackendAndReadsFreshAdState() throws Exception {
        FakeTransport transport = new FakeTransport();
        BackendRemoteAdDataSource dataSource = new BackendRemoteAdDataSource(transport);

        DataResult<AdItem> result = dataSource.updateInteraction("ad_001", InteractionAction.LIKE);

        assertEquals(DataResult.Status.SUCCESS, result.getStatus());
        assertTrue(result.getData().getInteractionState().isLiked());
        assertEquals(321, result.getData().getStats().getLikeCount());
        assertTrue(transport.paths.contains("/v1/ads/ad_001/like"));
        assertTrue(transport.paths.contains("/v1/ads/ad_001"));
    }

    @Test
    public void toggleInteractionReadsCurrentStateBeforeChoosingMethod() throws Exception {
        FakeTransport transport = new FakeTransport();
        BackendRemoteAdDataSource dataSource = new BackendRemoteAdDataSource(transport);

        dataSource.updateInteraction("ad_001", InteractionAction.TOGGLE_LIKE);
        DataResult<AdItem> result = dataSource.updateInteraction("ad_001", InteractionAction.TOGGLE_LIKE);

        assertFalse(result.getData().getInteractionState().isLiked());
        assertEquals(1, transport.likePostCount);
        assertEquals(1, transport.likeDeleteCount);
    }

    private static final class FakeTransport implements BackendRemoteAdDataSource.Transport {
        private final List<String> paths = new ArrayList<>();
        private boolean liked;
        private int likePostCount;
        private int likeDeleteCount;

        @Override
        public String get(String path) {
            paths.add(path);
            if (path.startsWith("/v1/feed")) {
                return "{\"requestId\":\"req-test\",\"code\":\"OK\",\"message\":\"ok\",\"data\":"
                        + "{\"channel\":\"featured\",\"cursor\":\"page_1\",\"nextCursor\":\"page_2\","
                        + "\"hasMore\":false,\"totalCount\":1,\"items\":[" + adJson() + "]}}";
            }
            if (path.startsWith("/v1/ads/ad_001")) {
                return "{\"requestId\":\"req-test\",\"code\":\"OK\",\"message\":\"ok\",\"data\":{\"ad\":"
                        + adJson() + "}}";
            }
            return "{\"requestId\":\"req-test\",\"code\":\"NOT_FOUND\",\"message\":\"missing\",\"data\":null}";
        }

        @Override
        public String post(String path) {
            paths.add(path);
            if (path.endsWith("/like")) {
                liked = true;
                likePostCount++;
            }
            return "{\"requestId\":\"req-test\",\"code\":\"OK\",\"message\":\"ok\",\"data\":{}}";
        }

        @Override
        public String delete(String path) {
            paths.add(path);
            if (path.endsWith("/like")) {
                liked = false;
                likeDeleteCount++;
            }
            return "{\"requestId\":\"req-test\",\"code\":\"OK\",\"message\":\"ok\",\"data\":{}}";
        }

        private String adJson() {
            int likeCount = liked ? 321 : 320;
            return "{"
                    + "\"adId\":\"ad_001\","
                    + "\"title\":\"Runner Launch\","
                    + "\"subtitle\":\"Campus commute\","
                    + "\"description\":\"Light runner for student commute\","
                    + "\"cover\":{\"url\":\"https://cdn.test/runner.jpg\"},"
                    + "\"video\":null,"
                    + "\"adType\":\"LARGE_IMAGE\","
                    + "\"brand\":\"NBN Sports\","
                    + "\"category\":\"sport\","
                    + "\"tags\":[{\"name\":\"sport\"},{\"name\":\"student\"}],"
                    + "\"stats\":{\"likeCount\":" + likeCount
                    + ",\"collectCount\":180,\"shareCount\":46,\"exposureCount\":1680,\"clickCount\":268},"
                    + "\"interactionState\":{\"liked\":" + liked + ",\"collected\":false}"
                    + "}";
        }
    }
}
