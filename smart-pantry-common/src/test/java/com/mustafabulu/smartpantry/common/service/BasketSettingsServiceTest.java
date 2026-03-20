package com.mustafabulu.smartpantry.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.smartpantry.common.dto.response.BasketMinimumSettingsResponse;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BasketSettingsServiceTest {

    @Mock
    private OkHttpClient httpClient;

    @Mock
    private Call call;

    private BasketSettingsService service;

    @BeforeEach
    void setUp() {
        service = new BasketSettingsService(
                new ObjectMapper(),
                httpClient,
                new BigDecimal("120.00"),
                new BigDecimal("250.00"),
                "https://migros.example/cart",
                "cookie=value",
                "https://migros.example/referer",
                true,
                true,
                false
        );
    }

    @Test
    void returnsFallbackWhenCredentialsAreMissing() {
        BasketSettingsService serviceWithMissingConfig = new BasketSettingsService(
                new ObjectMapper(),
                httpClient,
                new BigDecimal("120.00"),
                new BigDecimal("250.00"),
                "",
                "",
                "https://migros.example/referer",
                true,
                true,
                false
        );

        BasketMinimumSettingsResponse response = serviceWithMissingConfig.getMinimumBasketSettings();

        assertEquals(new BigDecimal("120.00"), response.ysMinimumBasketAmount());
        assertEquals(new BigDecimal("250.00"), response.mgMinimumBasketAmount());
        verify(httpClient, never()).newCall(any());
    }

    @Test
    void resolvesMinimumBasketFromMigrosApi() throws Exception {
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(successResponse("""
                {"data":{"cartInfo":{"minimumRequiredRevenue":"₺1.234,50"}}}
                """));

        BasketMinimumSettingsResponse response = service.getMinimumBasketSettings();

        assertEquals(new BigDecimal("120.00"), response.ysMinimumBasketAmount());
        assertEquals(new BigDecimal("1234.50"), response.mgMinimumBasketAmount());

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(httpClient).newCall(requestCaptor.capture());
        Request request = requestCaptor.getValue();
        assertEquals("https://migros.example/cart", request.url().toString());
        assertEquals("cookie=value", request.header("Cookie"));
        assertEquals("application/json", request.header("Accept"));
        assertEquals("false", request.header("X-Device-PWA"));
    }

    @Test
    void fallsBackToConfiguredMigrosMinimumWhenApiResponseIsInvalid() throws Exception {
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(successResponse("""
                {"data":{"cartInfo":{"minimumRequiredRevenue":"invalid"}}}
                """));

        BasketMinimumSettingsResponse response = service.getMinimumBasketSettings();

        assertEquals(new BigDecimal("120.00"), response.ysMinimumBasketAmount());
        assertEquals(new BigDecimal("250.00"), response.mgMinimumBasketAmount());
    }

    @Test
    void fallsBackWhenHttpCallFails() throws Exception {
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("network down"));

        BasketMinimumSettingsResponse response = service.getMinimumBasketSettings();

        assertEquals(new BigDecimal("120.00"), response.ysMinimumBasketAmount());
        assertEquals(new BigDecimal("250.00"), response.mgMinimumBasketAmount());
    }

    @Test
    void fallsBackWhenResponseIsUnsuccessful() throws Exception {
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(errorResponse(502, "{\"error\":\"bad gateway\"}"));

        BasketMinimumSettingsResponse response = service.getMinimumBasketSettings();

        assertEquals(new BigDecimal("120.00"), response.ysMinimumBasketAmount());
        assertEquals(new BigDecimal("250.00"), response.mgMinimumBasketAmount());
    }

    @Test
    void normalizesNumericMinimumRequiredRevenue() throws Exception {
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(successResponse("""
                {"data":{"cartInfo":{"minimumRequiredRevenue":2500}}}
                """));

        BasketMinimumSettingsResponse response = service.getMinimumBasketSettings();

        assertEquals(0, new BigDecimal("25.00").compareTo(response.mgMinimumBasketAmount()));
    }

    private static Response successResponse(String body) {
        return responseBuilder(200, body);
    }

    private static Response errorResponse(int code, String body) {
        return responseBuilder(code, body);
    }

    private static Response responseBuilder(int code, String body) {
        return new Response.Builder()
                .request(new Request.Builder().url("https://migros.example/cart").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(code == 200 ? "OK" : "ERROR")
                .body(ResponseBody.create(body, MediaType.get("application/json")))
                .build();
    }
}
