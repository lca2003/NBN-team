package com.nbn.adfeed.ai.search;

import com.nbn.adfeed.data.mock.MockAdRepository;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.DataResult;
import com.nbn.adfeed.data.model.PageRequest;
import com.nbn.adfeed.data.model.PageResult;
import com.nbn.adfeed.data.model.SearchRequest;
import com.nbn.adfeed.data.remote.AiSearchApi;
import com.nbn.adfeed.data.remote.AiSearchRequest;
import com.nbn.adfeed.data.remote.AiSearchResponse;
import com.nbn.adfeed.data.remote.RemoteClientProvider;
import com.nbn.adfeed.data.repository.AdRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Response;

//基于 Retrofit 实现的远程 AI 搜索服务
public final class RemoteAiSearchService implements AiSearchService {
    //当远程 AI 服务不可用时，将返回此提示。
    private static final String FALLBACK_ANSWER = "AI搜索不可用，已使用本地/后端降级数据";
    private static final int FALLBACK_SEARCH_LIMIT = 100;
    private static final Executor DEFAULT_FALLBACK_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "nbn-ai-search-fallback");
        thread.setDaemon(true);
        return thread;
    });

    //远程 API 接口实例，用于按候选地址发起 AI 搜索请求。
    private final List<AiSearchApi> aiSearchApis;
    
    //本地降级数据源，在远程调用失败时用于基于关键词的广告匹配
    private final AdRepository fallbackRepository;
    private final Executor fallbackExecutor;

    //使用默认配置构造服务实例
    public RemoteAiSearchService() {
        this(RemoteClientProvider.createAiSearchApis(), new MockAdRepository());
    }

    //构造服务实例，允许注入自定义的远程 API 和降级数据源（便于单元测试）
    public RemoteAiSearchService(AiSearchApi aiSearchApi, AdRepository fallbackRepository) {
        this(singletonApi(aiSearchApi), fallbackRepository);
    }

    RemoteAiSearchService(
            AiSearchApi aiSearchApi,
            AdRepository fallbackRepository,
            Executor fallbackExecutor
    ) {
        this(singletonApi(aiSearchApi), fallbackRepository, fallbackExecutor);
    }

    public RemoteAiSearchService(List<? extends AiSearchApi> aiSearchApis, AdRepository fallbackRepository) {
        this(aiSearchApis, fallbackRepository, DEFAULT_FALLBACK_EXECUTOR);
    }

    RemoteAiSearchService(
            List<? extends AiSearchApi> aiSearchApis,
            AdRepository fallbackRepository,
            Executor fallbackExecutor
    ) {
        this.aiSearchApis = sanitizeApis(aiSearchApis);
        this.fallbackRepository = fallbackRepository == null ? new MockAdRepository() : fallbackRepository;
        this.fallbackExecutor = fallbackExecutor == null ? DEFAULT_FALLBACK_EXECUTOR : fallbackExecutor;
    }

    //异步执行 AI 搜索
    @Override
    public void search(String query, Callback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }

        String safeQuery = query == null ? "" : query;
        AiSearchRequest request = new AiSearchRequest(safeQuery);

        searchRemoteCandidate(0, request, safeQuery, callback);
    }

    private void searchRemoteCandidate(
            int apiIndex,
            AiSearchRequest request,
            String query,
            Callback callback
    ) {
        if (apiIndex >= aiSearchApis.size()) {
            dispatchFallbackResult(query, callback);
            return;
        }

        // 发起 Retrofit 异步网络请求
        try {
            aiSearchApis.get(apiIndex).search(request).enqueue(new retrofit2.Callback<AiSearchResponse>() {
                @Override
                public void onResponse(Call<AiSearchResponse> call, Response<AiSearchResponse> response) {
                    AiSearchResponse body = response.body();
                    // 请求成功且响应体非空时，返回正常结果
                    if (response.isSuccessful() && body != null) {
                        callback.onResult(new AiSearchResult(
                                body.getAnswer(),
                                body.getMatchedAdIds(),
                                body.isFallback()
                        ));
                        return;
                    }
                    // 响应失败（例如 HTTP 4xx/5xx）或体为空时，尝试下一个候选地址
                    searchRemoteCandidate(apiIndex + 1, request, query, callback);
                }

                @Override
                public void onFailure(Call<AiSearchResponse> call, Throwable throwable) {
                    // 网络异常（超时、连接失败等）时，尝试下一个候选地址
                    searchRemoteCandidate(apiIndex + 1, request, query, callback);
                }
            });
        } catch (RuntimeException exception) {
            searchRemoteCandidate(apiIndex + 1, request, query, callback);
        }
    }

    //创建降级搜索结果
    private void dispatchFallbackResult(String query, Callback callback) {
        fallbackExecutor.execute(() -> callback.onResult(createFallbackResult(query)));
    }

    private AiSearchResult createFallbackResult(String query) {
        List<AdItem> matchedAds = searchFallbackRepository(query);
        return new AiSearchResult(FALLBACK_ANSWER, toAdIds(matchedAds), true);
    }

    private List<AdItem> searchFallbackRepository(String query) {
        SearchRequest request = new SearchRequest(
                query,
                "",
                "",
                PageRequest.FIRST_CURSOR,
                FALLBACK_SEARCH_LIMIT,
                "ai_search_fallback"
        );
        DataResult<PageResult<AdItem>> result = fallbackRepository.searchAds(request);
        PageResult<AdItem> page = result == null ? null : result.getData();
        if (page != null && !page.getItems().isEmpty()) {
            return page.getItems();
        }
        return fallbackRepository.searchByKeyword(query);
    }

    //将广告列表转换为广告 ID 字符串列表
    private static List<String> toAdIds(List<AdItem> ads) {
        List<String> ids = new ArrayList<>();
        if (ads == null) {
            return ids;
        }

        for (AdItem ad : ads) {
            ids.add(ad.getId());
        }
        return ids;
    }

    private static List<AiSearchApi> singletonApi(AiSearchApi api) {
        if (api == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(api);
    }

    private static List<AiSearchApi> sanitizeApis(List<? extends AiSearchApi> apis) {
        if (apis == null || apis.isEmpty()) {
            return Collections.emptyList();
        }

        List<AiSearchApi> sanitizedApis = new ArrayList<>();
        for (AiSearchApi api : apis) {
            if (api != null) {
                sanitizedApis.add(api);
            }
        }
        return Collections.unmodifiableList(sanitizedApis);
    }
}
