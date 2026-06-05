package com.nbn.adfeed.data.remote;

import okhttp3.OkHttpClient;

import org.junit.Test;

import retrofit2.Retrofit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class RemoteClientProviderTest {
    @Test
    public void defaultBaseUrlTargetsAdbReverseLoopback() {
        assertEquals("http://127.0.0.1:8081/", RemoteClientProvider.DEFAULT_BASE_URL);
    }

    @Test
    public void retrofitUsesBaseUrlAndTimeouts() {
        Retrofit retrofit = RemoteClientProvider.createRetrofit("http://localhost:8080/");

        assertEquals("http://localhost:8080/", retrofit.baseUrl().toString());
        assertTrue(retrofit.callFactory() instanceof OkHttpClient);

        OkHttpClient client = (OkHttpClient) retrofit.callFactory();
        assertEquals(5000, client.connectTimeoutMillis());
        assertEquals(10000, client.readTimeoutMillis());
    }

    @Test
    public void createsAiSearchApi() {
        assertNotNull(RemoteClientProvider.createAiSearchApi());
    }
}
