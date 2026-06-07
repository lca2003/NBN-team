package com.nbn.adfeed.data.remote;

import okhttp3.OkHttpClient;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import retrofit2.Retrofit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class RemoteClientProviderTest {
    @Test
    public void defaultBaseUrlTargetsEmulatorHostPort8081() {
        assertEquals("http://10.0.2.2:8081/", RemoteClientProvider.DEFAULT_BASE_URL);
    }

    @Test
    public void defaultBaseUrlsTryEmulatorThenAdbReverse() {
        List<String> urls = RemoteClientProvider.defaultBaseUrls();

        assertEquals(
                Arrays.asList("http://10.0.2.2:8081/", "http://127.0.0.1:8081/"),
                urls
        );
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

    @Test
    public void createsAiSearchApisForDefaultBaseUrls() {
        assertEquals(2, RemoteClientProvider.createAiSearchApis().size());
    }
}
