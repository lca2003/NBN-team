package com.nbn.adfeed.ai.search;

import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.DataResult;
import com.nbn.adfeed.data.model.InteractionState;
import com.nbn.adfeed.data.model.PageResult;
import com.nbn.adfeed.data.model.SearchRequest;
import com.nbn.adfeed.data.remote.AiSearchApi;
import com.nbn.adfeed.data.remote.AiSearchRequest;
import com.nbn.adfeed.data.remote.AiSearchResponse;
import com.nbn.adfeed.data.repository.AdRepository;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import okhttp3.Request;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RemoteAiSearchServiceTest {
    @Test
    public void searchSendsQueryOnlyRequestAndReturnsRemoteResult() {
        CapturingAiSearchApi api = new CapturingAiSearchApi(
                ImmediateCall.success(new AiSearchResponse(
                        "remote answer",
                        Arrays.asList("ad_010", "ad_011"),
                        false
                ))
        );
        RemoteAiSearchService service = new RemoteAiSearchService(api, new FixedAdRepository(Collections.emptyList()));
        List<AiSearchResult> results = new ArrayList<>();

        service.search("student sports", results::add);

        assertEquals("student sports", api.lastRequest.getQuery());
        assertEquals(1, results.size());
        assertEquals("remote answer", results.get(0).getAnswer());
        assertEquals(Arrays.asList("ad_010", "ad_011"), results.get(0).getMatchedAdIds());
        assertFalse(results.get(0).isFallback());
    }

    @Test
    public void searchFallsBackToLocalRepositoryWhenRemoteFails() {
        CapturingAiSearchApi api = new CapturingAiSearchApi(
                ImmediateCall.failure(new IOException("network unavailable"))
        );
        FixedAdRepository repository = new FixedAdRepository(Arrays.asList(
                ad("ad_001"),
                ad("ad_002")
        ));
        RemoteAiSearchService service = new RemoteAiSearchService(
                api,
                repository,
                Runnable::run
        );
        List<AiSearchResult> results = new ArrayList<>();

        service.search("sports", results::add);

        assertEquals("sports", api.lastRequest.getQuery());
        assertEquals(100, repository.lastSearchRequest.getPageSize());
        assertEquals(1, results.size());
        assertEquals(Arrays.asList("ad_001", "ad_002"), results.get(0).getMatchedAdIds());
        assertTrue(results.get(0).isFallback());
    }

    @Test
    public void searchTriesNextRemoteApiBeforeLocalFallback() {
        CapturingAiSearchApi firstApi = new CapturingAiSearchApi(
                ImmediateCall.failure(new IOException("emulator route unavailable"))
        );
        CapturingAiSearchApi secondApi = new CapturingAiSearchApi(
                ImmediateCall.success(new AiSearchResponse(
                        "second endpoint answer",
                        Collections.singletonList("ad_003"),
                        false
                ))
        );
        RemoteAiSearchService service = new RemoteAiSearchService(
                Arrays.asList(firstApi, secondApi),
                new FixedAdRepository(Collections.singletonList(ad("fallback_ad")))
        );
        List<AiSearchResult> results = new ArrayList<>();

        service.search("student sports", results::add);

        assertEquals("student sports", firstApi.lastRequest.getQuery());
        assertEquals("student sports", secondApi.lastRequest.getQuery());
        assertEquals(1, results.size());
        assertEquals("second endpoint answer", results.get(0).getAnswer());
        assertEquals(Collections.singletonList("ad_003"), results.get(0).getMatchedAdIds());
        assertFalse(results.get(0).isFallback());
    }

    @Test
    public void remoteFailureRunsFallbackRepositoryOnInjectedExecutor() {
        CapturingAiSearchApi api = new CapturingAiSearchApi(
                ImmediateCall.failure(new IOException("remote unavailable"))
        );
        FixedAdRepository repository = new FixedAdRepository(Collections.singletonList(ad("ad_001")));
        ManualExecutor executor = new ManualExecutor();
        RemoteAiSearchService service = new RemoteAiSearchService(
                Collections.singletonList(api),
                repository,
                executor
        );
        List<AiSearchResult> results = new ArrayList<>();

        service.search("sports", results::add);

        assertEquals(0, repository.searchAdsCallCount);
        assertTrue(results.isEmpty());

        executor.runNext();

        assertEquals(1, repository.searchAdsCallCount);
        assertEquals(1, results.size());
        assertEquals(Collections.singletonList("ad_001"), results.get(0).getMatchedAdIds());
    }

    private static AdItem ad(String id) {
        return new AdItem(
                id,
                "title",
                "brand",
                "channel",
                "summary",
                AdContentType.SMALL_IMAGE,
                Collections.emptyList(),
                new InteractionState()
        );
    }

    private static final class CapturingAiSearchApi implements AiSearchApi {
        private final Call<AiSearchResponse> call;
        private AiSearchRequest lastRequest;

        private CapturingAiSearchApi(Call<AiSearchResponse> call) {
            this.call = call;
        }

        @Override
        public Call<AiSearchResponse> search(AiSearchRequest request) {
            lastRequest = request;
            return call;
        }
    }

    private static final class FixedAdRepository implements AdRepository {
        private final List<AdItem> searchResult;
        private SearchRequest lastSearchRequest;
        private int searchAdsCallCount;

        private FixedAdRepository(List<AdItem> searchResult) {
            this.searchResult = searchResult;
        }

        @Override
        public List<AdItem> getInitialAds() {
            return Collections.emptyList();
        }

        @Override
        public List<AdItem> getAdsByChannel(String channel) {
            return Collections.emptyList();
        }

        @Override
        public List<AdItem> searchByKeyword(String keyword) {
            return searchResult;
        }

        @Override
        public DataResult<PageResult<AdItem>> searchAds(SearchRequest request) {
            searchAdsCallCount++;
            lastSearchRequest = request;
            return DataResult.success(
                    new PageResult<>(
                            searchResult,
                            request.getCursor(),
                            null,
                            false,
                            request.toPageRequest().getPageNumber(),
                            request.getPageSize(),
                            searchResult.size(),
                            "test"
                    ),
                    "test"
            );
        }
    }

    private static final class ManualExecutor implements Executor {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private void runNext() {
            tasks.remove(0).run();
        }
    }

    private static final class ImmediateCall implements Call<AiSearchResponse> {
        private final AiSearchResponse response;
        private final Throwable failure;

        private ImmediateCall(AiSearchResponse response, Throwable failure) {
            this.response = response;
            this.failure = failure;
        }

        private static ImmediateCall success(AiSearchResponse response) {
            return new ImmediateCall(response, null);
        }

        private static ImmediateCall failure(Throwable failure) {
            return new ImmediateCall(null, failure);
        }

        @Override
        public Response<AiSearchResponse> execute() throws IOException {
            if (failure != null) {
                if (failure instanceof IOException) {
                    throw (IOException) failure;
                }
                throw new IOException(failure);
            }
            return Response.success(response);
        }

        @Override
        public void enqueue(Callback<AiSearchResponse> callback) {
            if (failure != null) {
                callback.onFailure(this, failure);
                return;
            }
            callback.onResponse(this, Response.success(response));
        }

        @Override
        public boolean isExecuted() {
            return false;
        }

        @Override
        public void cancel() {
        }

        @Override
        public boolean isCanceled() {
            return false;
        }

        @Override
        public Call<AiSearchResponse> clone() {
            return new ImmediateCall(response, failure);
        }

        @Override
        public Request request() {
            return new Request.Builder().url("http://localhost/v1/ai/search").build();
        }

        @Override
        public Timeout timeout() {
            return Timeout.NONE;
        }
    }
}
