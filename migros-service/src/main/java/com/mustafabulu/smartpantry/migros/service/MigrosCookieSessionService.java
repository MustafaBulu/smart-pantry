package com.mustafabulu.smartpantry.migros.service;

import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.common.dto.response.MigrosCookieSessionResponse;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "marketplace.mg", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigrosCookieSessionService {
    private static final String SOURCE_SELENIUM_CACHE = "SELENIUM_CACHE";

    private final AtomicReference<CachedSession> cachedSession = new AtomicReference<>(null);

    private final boolean seleniumRefreshEnabled;
    private final String seleniumTargetUrl;
    private final String seleniumUserAgent;
    private final long seleniumWaitMs;
    private final Object seleniumWaitLock = new Object();

    public MigrosCookieSessionService(
            @Value("${migros.cookie.refresh.selenium.enabled:false}") boolean seleniumRefreshEnabled,
            @Value("${migros.cookie.refresh.selenium.url:https://www.migros.com.tr/}") String seleniumTargetUrl,
            @Value("${migros.cookie.refresh.selenium.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36}") String seleniumUserAgent,
            @Value("${migros.cookie.refresh.selenium.wait-ms:2500}") long seleniumWaitMs
    ) {
        this.seleniumRefreshEnabled = seleniumRefreshEnabled;
        this.seleniumTargetUrl = seleniumTargetUrl;
        this.seleniumUserAgent = seleniumUserAgent;
        this.seleniumWaitMs = seleniumWaitMs;
    }

    public synchronized MigrosCookieSessionResponse refreshFromSelenium() {
        if (!seleniumRefreshEnabled) {
            throw new SPException(
                    HttpStatus.BAD_REQUEST,
                    "MIGROS_SELENIUM_REFRESH_DISABLED",
                    "Migros selenium cookie refresh kapali."
            );
        }

        WebDriver driver = null;
        try {
            driver = new ChromeDriver(buildOptions(seleniumUserAgent));
            driver.get(seleniumTargetUrl);
            waitForSeleniumPageData();

            Set<org.openqa.selenium.Cookie> cookies = driver.manage().getCookies();
            String cookieHeader = cookies.stream()
                    .sorted(Comparator.comparing(org.openqa.selenium.Cookie::getName))
                    .map(cookie -> cookie.getName() + "=" + cookie.getValue())
                    .collect(Collectors.joining("; "));

            Map<String, String> localStorage = executeStorageScript(driver, "window.localStorage");
            Map<String, String> sessionStorage = executeStorageScript(driver, "window.sessionStorage");

            CachedSession snapshot = new CachedSession(
                    cookieHeader,
                    LocalDateTime.now(),
                    cookies.stream().map(org.openqa.selenium.Cookie::getName).sorted().toList(),
                    new ArrayList<>(localStorage.keySet()),
                    new ArrayList<>(sessionStorage.keySet())
            );
            cachedSession.set(snapshot);
            return toResponse(snapshot);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SPException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "MIGROS_SELENIUM_INTERRUPTED",
                    exception.getMessage()
            );
        } catch (Exception exception) {
            throw new SPException(
                    HttpStatus.BAD_GATEWAY,
                    "MIGROS_SELENIUM_REFRESH_FAILED",
                    exception.getMessage()
            );
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    public MigrosCookieSessionResponse getStatus(String fallbackCookie) {
        CachedSession current = cachedSession.get();
        if (current != null && current.cookieHeader() != null && !current.cookieHeader().isBlank()) {
            return toResponse(current);
        }
        if (fallbackCookie != null && !fallbackCookie.isBlank()) {
            List<String> names = parseCookieNames(fallbackCookie);
            return new MigrosCookieSessionResponse(
                    true,
                    "CONFIG_FALLBACK",
                    null,
                    names.size(),
                    names,
                    List.of(),
                    List.of()
            );
        }
        return new MigrosCookieSessionResponse(
                false,
                "NONE",
                null,
                0,
                List.of(),
                List.of(),
                List.of()
        );
    }

    public String resolveCookie(String fallbackCookie) {
        CachedSession current = cachedSession.get();
        if (current != null && current.cookieHeader() != null && !current.cookieHeader().isBlank()) {
            return current.cookieHeader();
        }
        return fallbackCookie;
    }

    private ChromeOptions buildOptions(String userAgent) {
        ChromeOptions options = new ChromeOptions();
        String chromeBin = System.getenv("CHROME_BIN");
        if (chromeBin != null && !chromeBin.isBlank()) {
            options.setBinary(chromeBin);
        }
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("profile.managed_default_content_settings.images", 2);
        options.setExperimentalOption("prefs", prefs);
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        options.addArguments("--headless");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-setuid-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        if (userAgent != null && !userAgent.isBlank()) {
            options.addArguments("--user-agent=" + userAgent);
        }
        return options;
    }

    private Map<String, String> executeStorageScript(WebDriver driver, String storageExpression) {
        if (!(driver instanceof JavascriptExecutor javascriptExecutor)) {
            return Map.of();
        }
        String script = """
                const data = {};
                const store = %s;
                for (let i = 0; i < store.length; i++) {
                  const key = store.key(i);
                  data[key] = store.getItem(key);
                }
                return data;
                """.formatted(storageExpression);
        Object result = javascriptExecutor.executeScript(script);
        if (!(result instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, String> casted = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            casted.put(String.valueOf(entry.getKey()), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
        }
        return casted;
    }

    private MigrosCookieSessionResponse toResponse(CachedSession snapshot) {
        List<String> cookieNames = snapshot.cookieNames() == null ? List.of() : snapshot.cookieNames();
        List<String> localKeys = snapshot.localStorageKeys() == null ? List.of() : snapshot.localStorageKeys();
        List<String> sessionKeys = snapshot.sessionStorageKeys() == null ? List.of() : snapshot.sessionStorageKeys();
        return new MigrosCookieSessionResponse(
                snapshot.cookieHeader() != null && !snapshot.cookieHeader().isBlank(),
                SOURCE_SELENIUM_CACHE,
                snapshot.refreshedAt() == null ? null : snapshot.refreshedAt().toString(),
                cookieNames.size(),
                cookieNames,
                localKeys,
                sessionKeys
        );
    }

    private void waitForSeleniumPageData() throws InterruptedException {
        long waitMs = Math.max(seleniumWaitMs, 0);
        if (waitMs == 0) {
            return;
        }
        long deadlineMs = System.currentTimeMillis() + waitMs;
        synchronized (seleniumWaitLock) {
            long remainingMs = waitMs;
            while (remainingMs > 0) {
                seleniumWaitLock.wait(remainingMs);
                remainingMs = deadlineMs - System.currentTimeMillis();
            }
        }
    }

    private List<String> parseCookieNames(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return List.of();
        }
        return Arrays.stream(cookieHeader.split(";"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .map(token -> {
                    int separator = token.indexOf('=');
                    return separator > 0 ? token.substring(0, separator).trim() : token;
                })
                .filter(name -> !name.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private record CachedSession(
            String cookieHeader,
            LocalDateTime refreshedAt,
            List<String> cookieNames,
            List<String> localStorageKeys,
            List<String> sessionStorageKeys
    ) {
        private CachedSession {
            Objects.requireNonNull(cookieNames);
            Objects.requireNonNull(localStorageKeys);
            Objects.requireNonNull(sessionStorageKeys);
        }
    }
}
