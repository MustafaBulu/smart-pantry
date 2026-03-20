package com.mustafabulu.smartpantry.migros.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.common.dto.request.MigrosStoreIdByLocationRequest;
import com.mustafabulu.smartpantry.common.dto.response.MigrosStoreIdByLocationResponse;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigrosStoreIdResolverServiceTest {

    @Mock
    private OkHttpClient httpClient;

    @Mock
    private MigrosCookieSessionService migrosCookieSessionService;

    @Mock
    private Call call;

    private MigrosStoreIdResolverService service;

    @BeforeEach
    void setUp() {
        service = new MigrosStoreIdResolverService(
                httpClient,
                new ObjectMapper(),
                "https://migros.example/delivery",
                "https://migros.example/checkouts",
                "https://migros.example/cart",
                "https://migros.example/cart-store-id",
                "delivery-cookie",
                "cart-cookie",
                "https://migros.example/",
                true,
                true,
                false,
                "https://migros.example/locations",
                true,
                Duration.ofHours(24),
                6,
                migrosCookieSessionService
        );
    }

    @Test
    void resolvesStoreIdFromCheckoutLookupAndCachesIt() throws Exception {
        when(migrosCookieSessionService.resolveCookie("delivery-cookie")).thenReturn("cookie=value");
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute())
                .thenReturn(response(200, "{\"data\":{\"cartInfo\":{\"line\":{\"id\":123}}}}"))
                .thenReturn(response(200, "{\"storeInfo\":{\"id\":987654321}}"));

        MigrosStoreIdByLocationRequest request = new MigrosStoreIdByLocationRequest(41.0, 29.0);
        MigrosStoreIdByLocationResponse first = service.resolveStoreId(request);
        MigrosStoreIdByLocationResponse second = service.resolveStoreId(request);

        assertEquals(987654321L, first.storeId());
        assertEquals(987654321L, second.storeId());
        verify(httpClient, times(2)).newCall(any(Request.class));
    }

    @Test
    void resolvesStoreIdFromCartSnapshotWhenCheckoutLookupFails() throws Exception {
        when(migrosCookieSessionService.resolveCookie("delivery-cookie")).thenReturn("cookie=value");
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute())
                .thenReturn(response(200, "{\"data\":{\"cartInfo\":{\"line\":{}}}}"))
                .thenReturn(response(200, "{\"data\":{\"storeId\":777}}"));

        MigrosStoreIdByLocationResponse response = service.resolveStoreId(
                new MigrosStoreIdByLocationRequest(41.1, 29.1)
        );

        assertEquals(777L, response.storeId());
    }

    @Test
    void throwsWhenCookieCannotBeResolved() {
        MigrosStoreIdResolverService serviceWithBlankCookie = new MigrosStoreIdResolverService(
                httpClient,
                new ObjectMapper(),
                "https://migros.example/delivery",
                "https://migros.example/checkouts",
                "https://migros.example/cart",
                "https://migros.example/cart-store-id",
                "",
                "",
                "https://migros.example/",
                true,
                true,
                false,
                "https://migros.example/locations",
                true,
                Duration.ofHours(24),
                6,
                migrosCookieSessionService
        );
        when(migrosCookieSessionService.resolveCookie("")).thenReturn("");
        when(migrosCookieSessionService.refreshFromSelenium()).thenThrow(new RuntimeException("selenium failed"));

        SPException exception = assertThrows(
                SPException.class,
                () -> serviceWithBlankCookie.resolveStoreId(new MigrosStoreIdByLocationRequest(41.0, 29.0))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("MIGROS_DELIVERY_COOKIE_REQUIRED", exception.getReason());
    }

    @Test
    void throwsWhenDeliveryEndpointReturnsEmptyBody() throws Exception {
        when(migrosCookieSessionService.resolveCookie("delivery-cookie")).thenReturn("cookie=value");
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute())
                .thenReturn(response(200, "{\"data\":{\"cartInfo\":{\"line\":{}}}}"))
                .thenReturn(response(200, "{\"data\":{}}"))
                .thenReturn(response(200, "[{\"id\":1,\"name\":\"City\",\"latitude\":41.0,\"longitude\":29.0}]"))
                .thenReturn(response(200, "[{\"id\":2,\"name\":\"Town\",\"latitude\":41.0,\"longitude\":29.0}]"))
                .thenReturn(response(200, "[{\"id\":3,\"name\":\"District\",\"latitude\":41.0,\"longitude\":29.0}]"))
                .thenReturn(new Response.Builder()
                        .request(new Request.Builder().url("https://migros.example/delivery").build())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .build());

        SPException exception = assertThrows(
                SPException.class,
                () -> service.resolveStoreId(new MigrosStoreIdByLocationRequest(41.0, 29.0))
        );

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertEquals("MIGROS_DELIVERY_EMPTY_RESPONSE", exception.getReason());
    }

    @Test
    void throwsWhenLocationRequestFails() throws Exception {
        when(migrosCookieSessionService.resolveCookie("delivery-cookie")).thenReturn("cookie=value");
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute())
                .thenReturn(response(200, "{\"data\":{\"cartInfo\":{\"line\":{}}}}"))
                .thenReturn(response(200, "{\"data\":{}}"))
                .thenThrow(new IOException("locations down"));

        SPException exception = assertThrows(
                SPException.class,
                () -> service.resolveStoreId(new MigrosStoreIdByLocationRequest(41.0, 29.0))
        );

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertEquals("MIGROS_LOCATION_REQUEST_FAILED", exception.getReason());
    }

    @Test
    void resolvesStoreIdFromDeliveryEndpointWhenFallbackEndpointsMiss() throws Exception {
        when(migrosCookieSessionService.resolveCookie("delivery-cookie")).thenReturn("cookie=value");
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute())
                .thenReturn(response(200, "{\"data\":{\"cartInfo\":{\"line\":{}}}}"))
                .thenReturn(response(200, "{\"data\":{}}"))
                .thenReturn(response(200, """
                        [
                          {"id":34,"name":"Istanbul","latitude":41.01,"longitude":29.00},
                          {"id":35,"name":"Izmir","latitude":38.42,"longitude":27.14}
                        ]
                        """))
                .thenReturn(response(200, """
                        [
                          {"id":3401,"name":"Kadikoy","latitude":40.99,"longitude":29.03},
                          {"id":3402,"name":"Besiktas","latitude":41.04,"longitude":29.01}
                        ]
                        """))
                .thenReturn(response(200, """
                        [
                          {"id":340101,"name":"Moda","latitude":40.985,"longitude":29.026},
                          {"id":340102,"name":"Erenkoy","latitude":40.975,"longitude":29.07}
                        ]
                        """))
                .thenReturn(response(200, "{\"storeId\":456789}"));

        MigrosStoreIdByLocationResponse result = service.resolveStoreId(
                new MigrosStoreIdByLocationRequest(40.986, 29.027)
        );

        assertEquals(456789L, result.storeId());
    }

    private static Response response(int code, String body) {
        return new Response.Builder()
                .request(new Request.Builder().url("https://migros.example").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(code == 200 ? "OK" : "ERROR")
                .body(ResponseBody.create(body, MediaType.get("application/json")))
                .build();
    }
}
