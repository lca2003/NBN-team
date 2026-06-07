package com.nbn.adfeed.data.remote;

import org.junit.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class AiSearchApiTest {
    @Test
    public void searchDescribesBackendEndpoint() throws NoSuchMethodException {
        Method search = AiSearchApi.class.getMethod("search", AiSearchRequest.class);

        POST post = search.getAnnotation(POST.class);

        assertNotNull(post);
        assertEquals("/api/ai/search", post.value());
        assertEquals(Call.class, search.getReturnType());
        assertTrue(hasBodyAnnotation(search.getParameterAnnotations()[0]));
    }

    private static boolean hasBodyAnnotation(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().equals(Body.class)) {
                return true;
            }
        }
        return false;
    }
}
