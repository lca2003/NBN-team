package com.nbn.adfeed.data.remote;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

// 网络基础设施客户端
//用于创建 {@link AiSearchApi} 的 Retrofit 客户端实例，封装了 OkHttpClient 的超时配置
public final class RemoteClientProvider {
    //用于连接本地开发服务器（Android 模拟器访问宿主机时使用 10.0.2.2）
    public static final String DEFAULT_BASE_URL = "http://10.0.2.2:8080/";

    private static final long CONNECT_TIMEOUT_SECONDS = 5L;
    private static final long READ_TIMEOUT_SECONDS = 10L;

    private RemoteClientProvider() {
    }

    public static AiSearchApi createAiSearchApi() {
        return createAiSearchApi(DEFAULT_BASE_URL);
    }

    public static AiSearchApi createAiSearchApi(String baseUrl) {
        return createRetrofit(baseUrl).create(AiSearchApi.class);
    }


    /**
     * 构建并配置 Retrofit 实例。
     * <p>
     * 该方法会创建一个带有连接超时和读取超时设置的 OkHttpClient，然后构建 Retrofit 客户端。
     * 使用 {@link GsonConverterFactory} 作为 JSON 序列化/反序列化工具。
     * </p>
     *
     * @param baseUrl API 的基础 URL（将被规范化处理）
     * @return 配置好的 Retrofit 实例
     * @throws IllegalArgumentException 如果 baseUrl 无效（由 {@link #normalizeBaseUrl(String)} 抛出）
     */
    static Retrofit createRetrofit(String baseUrl) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();

        return new Retrofit.Builder()
                .baseUrl(normalizeBaseUrl(baseUrl))
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    /**
     * 规范化基础 URL，确保其以 '/' 结尾。
     * <p>
     * 该方法会去除输入字符串首尾的空白字符，并检查是否为空。如果 URL 已以 '/' 结尾则直接返回，
     * 否则在末尾添加 '/'。
     * </p>
     *
     * @param baseUrl 需要规范化的原始 URL 字符串，不能为 null 或仅包含空白字符
     * @return 规范化后的 URL（以 '/' 结尾）
     * @throws IllegalArgumentException 如果 baseUrl 为 null 或仅包含空白字符
     */
    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }

        String normalized = baseUrl.trim();
        if (normalized.endsWith("/")) {
            return normalized;
        }
        return normalized + "/";
    }
}
