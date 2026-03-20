package com.mustafabulu.smartpantry.common.config;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpClientConfigTest {

    @Test
    void okHttpClientUsesExpectedTimeouts() {
        OkHttpClient client = new HttpClientConfig().okHttpClient();

        assertEquals(10_000, client.connectTimeoutMillis());
        assertEquals(30_000, client.readTimeoutMillis());
        assertEquals(30_000, client.writeTimeoutMillis());
    }
}
