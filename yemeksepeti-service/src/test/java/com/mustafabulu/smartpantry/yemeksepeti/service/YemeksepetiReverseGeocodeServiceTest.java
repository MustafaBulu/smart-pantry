package com.mustafabulu.smartpantry.yemeksepeti.service;

import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.common.dto.request.YemeksepetiReverseGeocodeRequest;
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
import org.springframework.http.HttpStatus;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YemeksepetiReverseGeocodeServiceTest {

    @Mock
    private OkHttpClient httpClient;

    @Mock
    private Call call;

    private YemeksepetiReverseGeocodeService service;

    @BeforeEach
    void setUp() {
        service = new YemeksepetiReverseGeocodeService(
                httpClient,
                "https://geo.example/reverse",
                "tr_TR",
                "tr,en;q=0.9",
                "Bearer token",
                "cust-code",
                "client-id",
                "session-id",
                "https://www.yemeksepeti.com",
                "https://www.yemeksepeti.com/",
                "agent",
                "tr",
                "b2c",
                "YS_TR",
                "2"
        );
    }

    @Test
    void returnsResponseBodyWhenRequestSucceeds() throws Exception {
        YemeksepetiReverseGeocodeRequest request = new YemeksepetiReverseGeocodeRequest(41.0, 29.0);
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response(200, "{\"district\":\"Besiktas\"}"));

        String body = service.reverse(request);

        assertEquals("{\"district\":\"Besiktas\"}", body);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(httpClient).newCall(requestCaptor.capture());
        Request sentRequest = requestCaptor.getValue();
        assertEquals(
                "https://geo.example/reverse?lat=41.0&lng=29.0&language=tr_TR",
                sentRequest.url().toString()
        );
        assertEquals("Bearer token", sentRequest.header("Authorization"));
        assertEquals("client-id", sentRequest.header("perseus-client-id"));
    }

    @Test
    void throwsWhenResponseBodyIsMissing() throws Exception {
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(new Response.Builder()
                .request(new Request.Builder().url("https://geo.example/reverse").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build());
        YemeksepetiReverseGeocodeRequest request = new YemeksepetiReverseGeocodeRequest(41.0, 29.0);

        SPException exception = assertThrows(
                SPException.class,
                () -> service.reverse(request)
        );

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertEquals("YEMEKSEPETI_GEOCODER_EMPTY_RESPONSE", exception.getReason());
    }

    @Test
    void throwsWhenResponseIsUnsuccessful() throws Exception {
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response(502, "{\"error\":\"bad gateway\"}"));
        YemeksepetiReverseGeocodeRequest request = new YemeksepetiReverseGeocodeRequest(41.0, 29.0);

        SPException exception = assertThrows(
                SPException.class,
                () -> service.reverse(request)
        );

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertEquals("YEMEKSEPETI_GEOCODER_HTTP_502", exception.getReason());
    }

    @Test
    void throwsWhenHttpClientFails() throws Exception {
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("timeout"));
        YemeksepetiReverseGeocodeRequest request = new YemeksepetiReverseGeocodeRequest(41.0, 29.0);

        SPException exception = assertThrows(
                SPException.class,
                () -> service.reverse(request)
        );

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertEquals("YEMEKSEPETI_GEOCODER_REQUEST_FAILED", exception.getReason());
    }

    private static Response response(int code, String body) {
        return new Response.Builder()
                .request(new Request.Builder().url("https://geo.example/reverse").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(code == 200 ? "OK" : "ERROR")
                .body(ResponseBody.create(body, MediaType.get("application/json")))
                .build();
    }
}
