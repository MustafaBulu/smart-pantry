package com.mustafabulu.smartpantry.migros.controller;

import com.mustafabulu.smartpantry.common.dto.request.MigrosStoreIdByLocationRequest;
import com.mustafabulu.smartpantry.common.dto.response.MigrosCookieSessionResponse;
import com.mustafabulu.smartpantry.common.dto.response.MigrosStoreIdByLocationResponse;
import com.mustafabulu.smartpantry.migros.service.MigrosCookieSessionService;
import com.mustafabulu.smartpantry.migros.service.MigrosStoreIdResolverService;
import com.mustafabulu.smartpantry.common.service.SettingsAccessGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/settings/migros")
@ConditionalOnProperty(prefix = "marketplace.mg", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigrosSettingsController {

    private final MigrosStoreIdResolverService migrosStoreIdResolverService;
    private final MigrosCookieSessionService migrosCookieSessionService;
    private final SettingsAccessGuard settingsAccessGuard;
    @Value("${migros.delivery.cookie:}")
    private String migrosDeliveryCookie;
    @Value("${migros.cart.cookie:}")
    private String migrosCartCookie;

    @PostMapping("/store-id-by-location")
    public ResponseEntity<MigrosStoreIdByLocationResponse> resolveMigrosStoreId(
            @Valid @RequestBody MigrosStoreIdByLocationRequest request
    ) {
        return ResponseEntity.ok(migrosStoreIdResolverService.resolveStoreId(request));
    }

    @PostMapping("/cookies/refresh")
    public ResponseEntity<MigrosCookieSessionResponse> refreshMigrosCookieSession(
            @RequestHeader(value = SettingsAccessGuard.SETTINGS_API_KEY_HEADER, required = false) String settingsApiKey
    ) {
        settingsAccessGuard.assertAuthorized(settingsApiKey);
        return ResponseEntity.ok(migrosCookieSessionService.refreshFromSelenium());
    }

    @GetMapping("/cookies/status")
    public ResponseEntity<MigrosCookieSessionResponse> getMigrosCookieSessionStatus(
            @RequestHeader(value = SettingsAccessGuard.SETTINGS_API_KEY_HEADER, required = false) String settingsApiKey
    ) {
        settingsAccessGuard.assertAuthorized(settingsApiKey);
        String fallbackCookie = (migrosDeliveryCookie == null || migrosDeliveryCookie.isBlank())
                ? migrosCartCookie
                : migrosDeliveryCookie;
        return ResponseEntity.ok(migrosCookieSessionService.getStatus(fallbackCookie));
    }
}
