package com.mustafabulu.smartpantry.yemeksepeti.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.smartpantry.common.config.MarketplaceUrlProperties;
import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.common.dto.request.YemeksepetiReverseGeocodeRequest;
import com.mustafabulu.smartpantry.common.dto.response.YemeksepetiVendorByLocationResponse;
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
class YemeksepetiVendorResolverServiceTest {

    @Mock
    private OkHttpClient httpClient;

    @Mock
    private Call call;

    private MarketplaceUrlProperties marketplaceUrlProperties;
    private YemeksepetiVendorResolverService service;

    @BeforeEach
    void setUp() {
        marketplaceUrlProperties = new MarketplaceUrlProperties();
        service = new YemeksepetiVendorResolverService(
                httpClient,
                new ObjectMapper(),
                "https://fd.example/vendors",
                "chain",
                "tr",
                "regular",
                "tr-TR",
                "https://www.yemeksepeti.com/",
                "client-id",
                "session-id",
                "2",
                "fp-key",
                "disco-id",
                "https://www.yemeksepeti.com",
                "agent",
                "https://fallback.example/vendor",
                marketplaceUrlProperties
        );
    }

    @Test
    void returnsResolvedVendorUrlAndPersistsIt() throws Exception {
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response(200, """
                {"data":{"items":[{"code":"vj0c","redirection_url":"https://vendor.example/path"}]}}
                """));

        YemeksepetiVendorByLocationResponse response = service.resolve(
                new YemeksepetiReverseGeocodeRequest(41.0, 29.0)
        );

        assertEquals("https://vendor.example/path", response.redirectionUrl());
        assertEquals("https://vendor.example/path", marketplaceUrlProperties.getYemeksepetiBase());

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(httpClient).newCall(requestCaptor.capture());
        assertEquals(
                "https://fd.example/vendors?chain_code=chain&country=tr&customer_type=regular&latitude=41.0&longitude=29.0",
                requestCaptor.getValue().url().toString()
        );
    }

    @Test
    void fallsBackToConfiguredBaseUrlWhen403IsReturned() throws Exception {
        marketplaceUrlProperties.setYemeksepetiBase("https://persisted.example/vendor");
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response(403, "{\"error\":\"blocked\"}"));

        YemeksepetiVendorByLocationResponse response = service.resolve(
                new YemeksepetiReverseGeocodeRequest(41.0, 29.0)
        );

        assertEquals("https://persisted.example/vendor", response.redirectionUrl());
    }

    @Test
    void buildsRestaurantUrlWhenRedirectionUrlIsMissing() throws Exception {
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response(200, """
                {"data":{"items":[{"vendor_code":"abc123"}]}}
                """));

        YemeksepetiVendorByLocationResponse response = service.resolve(
                new YemeksepetiReverseGeocodeRequest(41.0, 29.0)
        );

        assertEquals("https://yemeksepeti.com/restaurant/abc123", response.redirectionUrl());
    }

    @Test
    void throwsWhenItemsArrayIsMissing() throws Exception {
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response(200, "{\"data\":{\"items\":[]}}"));
        YemeksepetiReverseGeocodeRequest request = new YemeksepetiReverseGeocodeRequest(41.0, 29.0);

        SPException exception = assertThrows(
                SPException.class,
                () -> service.resolve(request)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void throwsWhenVendorCodeCannotBeResolved() throws Exception {
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response(200, """
                {"data":{"items":[{"redirection_url":"https://vendor.example/path"}]}}
                """));
        YemeksepetiReverseGeocodeRequest request = new YemeksepetiReverseGeocodeRequest(41.0, 29.0);

        SPException exception = assertThrows(
                SPException.class,
                () -> service.resolve(request)
        );

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
    }

    @Test
    void throwsWhenHttpClientFails() throws Exception {
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("timeout"));
        YemeksepetiReverseGeocodeRequest request = new YemeksepetiReverseGeocodeRequest(41.0, 29.0);

        SPException exception = assertThrows(
                SPException.class,
                () -> service.resolve(request)
        );

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertEquals("YEMEKSEPETI_VENDOR_REQUEST_FAILED", exception.getReason());
    }

    private static Response response(int code, String body) {
        return new Response.Builder()
                .request(new Request.Builder().url("https://fd.example/vendors").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(code == 200 ? "OK" : "ERROR")
                .body(ResponseBody.create(body, MediaType.get("application/json")))
                .build();
    }
}
