package com.mustafabulu.smartpantry.common.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Migros cookie session status")
public record MigrosCookieSessionResponse(
        @Schema(description = "Is a usable cookie available", example = "true")
        boolean available,
        @Schema(description = "Cookie source", example = "SELENIUM_CACHE")
        String source,
        @Schema(description = "Last refresh timestamp (ISO-8601)", example = "2026-02-24T23:55:10.123")
        String refreshedAt,
        @Schema(description = "Cookie name count", example = "7")
        int cookieCount,
        @Schema(description = "Cookie names captured from browser")
        List<String> cookieNames,
        @Schema(description = "localStorage keys captured from browser")
        List<String> localStorageKeys,
        @Schema(description = "sessionStorage keys captured from browser")
        List<String> sessionStorageKeys
) {
}
