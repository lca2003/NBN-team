package com.nbn.adfeed.data.remote;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AiSearchApi {
    @POST("/v1/ai/search")
    Call<AiSearchResponse> search(@Body AiSearchRequest request);
}
