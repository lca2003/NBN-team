package com.nbn.adfeed.ai.search;

//对话式搜索接口
public interface AiSearchService {
    void search(String query, Callback callback);

    interface Callback {
        void onResult(AiSearchResult result);
    }
}
