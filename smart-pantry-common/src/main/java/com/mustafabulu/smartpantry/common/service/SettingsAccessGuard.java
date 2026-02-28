package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.core.exception.SPException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class SettingsAccessGuard {
    public static final String SETTINGS_API_KEY_HEADER = "X-Settings-Api-Key";

    private final String settingsApiKey;

    public SettingsAccessGuard(@Value("${settings.api-key:}") String settingsApiKey) {
        this.settingsApiKey = settingsApiKey;
    }

    public void assertAuthorized(String providedApiKey) {
        if (settingsApiKey == null || settingsApiKey.isBlank()) {
            throw new SPException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SETTINGS_API_KEY_NOT_CONFIGURED",
                    "Settings API key is not configured."
            );
        }
        if (providedApiKey == null || providedApiKey.isBlank()) {
            throw new SPException(
                    HttpStatus.UNAUTHORIZED,
                    "SETTINGS_API_KEY_REQUIRED",
                    SETTINGS_API_KEY_HEADER + " header is required."
            );
        }
        boolean matches = MessageDigest.isEqual(
                settingsApiKey.getBytes(StandardCharsets.UTF_8),
                providedApiKey.getBytes(StandardCharsets.UTF_8)
        );
        if (!matches) {
            throw new SPException(
                    HttpStatus.UNAUTHORIZED,
                    "SETTINGS_API_KEY_INVALID",
                    "Invalid settings API key."
            );
        }
    }
}
