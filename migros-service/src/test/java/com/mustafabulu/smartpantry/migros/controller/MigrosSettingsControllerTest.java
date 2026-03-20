package com.mustafabulu.smartpantry.migros.controller;

import com.mustafabulu.smartpantry.common.dto.request.MigrosStoreIdByLocationRequest;
import com.mustafabulu.smartpantry.common.dto.response.MigrosCookieSessionResponse;
import com.mustafabulu.smartpantry.common.dto.response.MigrosStoreIdByLocationResponse;
import com.mustafabulu.smartpantry.common.service.SettingsAccessGuard;
import com.mustafabulu.smartpantry.migros.service.MigrosCookieSessionService;
import com.mustafabulu.smartpantry.migros.service.MigrosStoreIdResolverService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MigrosSettingsControllerTest {

    @Test
    void controllerDelegatesToServices() {
        MigrosStoreIdResolverService resolverService = mock(MigrosStoreIdResolverService.class);
        MigrosCookieSessionService cookieSessionService = mock(MigrosCookieSessionService.class);
        SettingsAccessGuard guard = mock(SettingsAccessGuard.class);
        MigrosSettingsController controller = new MigrosSettingsController(resolverService, cookieSessionService, guard);
        ReflectionTestUtils.setField(controller, "migrosDeliveryCookie", "");
        ReflectionTestUtils.setField(controller, "migrosCartCookie", "cart-cookie");

        var request = new MigrosStoreIdByLocationRequest(41.0, 29.0);
        var storeIdResponse = new MigrosStoreIdByLocationResponse(1L);
        var sessionResponse = new MigrosCookieSessionResponse(
                true,
                "SELENIUM_CACHE",
                "2026-03-20T10:15:30",
                2,
                java.util.List.of("delivery"),
                java.util.List.of("ls"),
                java.util.List.of("ss")
        );
        when(resolverService.resolveStoreId(request)).thenReturn(storeIdResponse);
        when(cookieSessionService.refreshFromSelenium()).thenReturn(sessionResponse);
        when(cookieSessionService.getStatus("cart-cookie")).thenReturn(sessionResponse);

        assertEquals(storeIdResponse, controller.resolveMigrosStoreId(request).getBody());
        assertEquals(sessionResponse, controller.refreshMigrosCookieSession("key").getBody());
        assertEquals(sessionResponse, controller.getMigrosCookieSessionStatus("key").getBody());
        verify(guard, times(2)).assertAuthorized("key");
    }
}
