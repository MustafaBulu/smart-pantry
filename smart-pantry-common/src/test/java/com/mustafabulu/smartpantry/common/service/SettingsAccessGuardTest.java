package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.core.exception.SPException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettingsAccessGuardTest {

    @Test
    void throwsWhenConfiguredApiKeyIsMissing() {
        SettingsAccessGuard guard = new SettingsAccessGuard("");

        SPException exception = assertThrows(SPException.class, () -> guard.assertAuthorized("provided"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        assertEquals("SETTINGS_API_KEY_NOT_CONFIGURED", exception.getReason());
    }

    @Test
    void throwsWhenProvidedApiKeyIsMissing() {
        SettingsAccessGuard guard = new SettingsAccessGuard("secret");

        SPException exception = assertThrows(SPException.class, () -> guard.assertAuthorized(" "));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("SETTINGS_API_KEY_REQUIRED", exception.getReason());
    }

    @Test
    void throwsWhenProvidedApiKeyDoesNotMatch() {
        SettingsAccessGuard guard = new SettingsAccessGuard("secret");

        SPException exception = assertThrows(SPException.class, () -> guard.assertAuthorized("wrong"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("SETTINGS_API_KEY_INVALID", exception.getReason());
    }

    @Test
    void authorizesWhenApiKeysMatch() {
        SettingsAccessGuard guard = new SettingsAccessGuard("secret");

        assertDoesNotThrow(() -> guard.assertAuthorized("secret"));
    }
}
