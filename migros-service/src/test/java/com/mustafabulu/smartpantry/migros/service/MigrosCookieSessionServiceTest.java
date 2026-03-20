package com.mustafabulu.smartpantry.migros.service;

import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.common.dto.response.MigrosCookieSessionResponse;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MigrosCookieSessionServiceTest {

    @Test
    void refreshFromSeleniumThrowsWhenDisabled() {
        MigrosCookieSessionService service = new MigrosCookieSessionService(false, "https://migros.example", "agent", 0);

        SPException exception = assertThrows(SPException.class, service::refreshFromSelenium);

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("MIGROS_SELENIUM_REFRESH_DISABLED", exception.getReason());
    }

    @Test
    void getStatusUsesCachedSessionWhenPresent() throws Exception {
        MigrosCookieSessionService service = new MigrosCookieSessionService(false, "https://migros.example", "agent", 0);
        setCachedSession(
                service,
                "a=1; b=2",
                List.of("a", "b"),
                List.of("ls1"),
                List.of("ss1")
        );

        MigrosCookieSessionResponse response = service.getStatus("fallback=1");

        assertTrue(response.available());
        assertEquals("SELENIUM_CACHE", response.source());
        assertEquals(2, response.cookieCount());
        assertEquals(List.of("a", "b"), response.cookieNames());
    }

    @Test
    void getStatusFallsBackToConfiguredCookie() {
        MigrosCookieSessionService service = new MigrosCookieSessionService(false, "https://migros.example", "agent", 0);

        MigrosCookieSessionResponse response = service.getStatus("migros=1; other=2; migros=3");

        assertTrue(response.available());
        assertEquals("CONFIG_FALLBACK", response.source());
        assertEquals(List.of("migros", "other"), response.cookieNames());
    }

    @Test
    void resolveCookiePrefersCachedCookie() throws Exception {
        MigrosCookieSessionService service = new MigrosCookieSessionService(false, "https://migros.example", "agent", 0);
        setCachedSession(service, "cached=1", List.of("cached"), List.of(), List.of());

        assertEquals("cached=1", service.resolveCookie("fallback=1"));
    }

    @Test
    void resolveCookieFallsBackWhenCacheIsEmpty() {
        MigrosCookieSessionService service = new MigrosCookieSessionService(false, "https://migros.example", "agent", 0);

        assertEquals("fallback=1", service.resolveCookie("fallback=1"));
    }

    @Test
    void getStatusReturnsUnavailableWhenNothingExists() {
        MigrosCookieSessionService service = new MigrosCookieSessionService(false, "https://migros.example", "agent", 0);

        MigrosCookieSessionResponse response = service.getStatus(" ");

        assertFalse(response.available());
        assertEquals("NONE", response.source());
    }

    @Test
    void buildOptionsIncludesExpectedArguments() throws Exception {
        MigrosCookieSessionService service = new MigrosCookieSessionService(true, "https://migros.example", "custom-agent", 0);
        Method method = MigrosCookieSessionService.class.getDeclaredMethod("buildOptions", String.class);
        method.setAccessible(true);

        Object result = method.invoke(service, "custom-agent");

        ChromeOptions options = assertInstanceOf(ChromeOptions.class, result);
        String serialized = options.asMap().toString();
        assertTrue(serialized.contains("--headless"));
        assertTrue(serialized.contains("--user-agent=custom-agent"));
    }

    @Test
    void executeStorageScriptReturnsEmptyWhenDriverIsNotJavascriptExecutor() throws Exception {
        MigrosCookieSessionService service = new MigrosCookieSessionService(true, "https://migros.example", "agent", 0);
        Method method = MigrosCookieSessionService.class.getDeclaredMethod(
                "executeStorageScript",
                WebDriver.class,
                String.class
        );
        method.setAccessible(true);

        Object result = method.invoke(service, mock(WebDriver.class), "window.localStorage");

        assertEquals(Map.of(), result);
    }

    @Test
    void parseCookieNamesSortsDeduplicatesAndKeepsTokensWithoutSeparator() throws Exception {
        MigrosCookieSessionService service = new MigrosCookieSessionService(true, "https://migros.example", "agent", 0);
        Method method = MigrosCookieSessionService.class.getDeclaredMethod("parseCookieNames", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(service, "b=2; tokenOnly; a=1; b=3");

        assertEquals(List.of("a", "b", "tokenOnly"), result);
    }

    @SuppressWarnings("unchecked")
    private static void setCachedSession(
            MigrosCookieSessionService service,
            String cookieHeader,
            List<String> cookieNames,
            List<String> localStorageKeys,
            List<String> sessionStorageKeys
    ) throws Exception {
        Class<?> cachedSessionClass = Class.forName(
                "com.mustafabulu.smartpantry.migros.service.MigrosCookieSessionService$CachedSession"
        );
        Constructor<?> constructor = cachedSessionClass.getDeclaredConstructor(
                String.class,
                LocalDateTime.class,
                List.class,
                List.class,
                List.class
        );
        constructor.setAccessible(true);
        Object cachedSession = constructor.newInstance(
                cookieHeader,
                LocalDateTime.now(),
                cookieNames,
                localStorageKeys,
                sessionStorageKeys
        );
        AtomicReference<Object> reference = (AtomicReference<Object>) ReflectionTestUtils.getField(service, "cachedSession");
        reference.set(cachedSession);
    }
}
