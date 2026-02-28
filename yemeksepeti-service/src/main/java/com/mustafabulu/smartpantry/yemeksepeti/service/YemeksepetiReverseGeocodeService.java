package com.mustafabulu.smartpantry.yemeksepeti.service;

import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.common.dto.request.YemeksepetiReverseGeocodeRequest;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Service
@ConditionalOnProperty(prefix = "marketplace.ys", name = "enabled", havingValue = "true", matchIfMissing = true)
public class YemeksepetiReverseGeocodeService {
    private static final String HEADER_CUST_CODE = buildHeaderName("cust", "code");
    private static final String HEADER_PERSEUS_CLIENT_ID = buildHeaderName("perseus", "client", "id");
    private static final String HEADER_PERSEUS_SESSION_ID = buildHeaderName("perseus", "session", "id");
    private static final String HEADER_X_CALLER_COUNTRY = buildHeaderName("x", "caller", "country");
    private static final String HEADER_X_CALLER_PLATFORM = buildHeaderName("x", "caller", "platform");
    private static final String HEADER_X_GLOBAL_ENTITY_ID = buildHeaderName("x", "global", "entity", "id");
    private static final String HEADER_X_PD_LANGUAGE_ID = buildHeaderName("x", "pd", "language", "id");

    private final OkHttpClient httpClient;

    private final String reverseUrl;
    private final String language;
    private final String acceptLanguage;
    private final String authorization;
    private final String custCode;
    private final String perseusClientId;
    private final String perseusSessionId;
    private final String origin;
    private final String referer;
    private final String userAgent;
    private final String callerCountry;
    private final String callerPlatform;
    private final String globalEntityId;
    private final String pdLanguageId;

    public YemeksepetiReverseGeocodeService(
            OkHttpClient httpClient,
            @Value("${yemeksepeti.geocoder.reverse-url:https://geocoder.deliveryhero.io/api/v2/reverse}") String reverseUrl,
            @Value("${yemeksepeti.geocoder.language:tr_TR}") String language,
            @Value("${yemeksepeti.geocoder.accept-language:tr,en;q=0.9,en-US;q=0.8}") String acceptLanguage,
            @Value("${yemeksepeti.geocoder.authorization:}") String authorization,
            @Value("${yemeksepeti.geocoder.cust-code:}") String custCode,
            @Value("${yemeksepeti.geocoder.perseus-client-id:}") String perseusClientId,
            @Value("${yemeksepeti.geocoder.perseus-session-id:}") String perseusSessionId,
            @Value("${yemeksepeti.geocoder.origin:https://www.yemeksepeti.com}") String origin,
            @Value("${yemeksepeti.geocoder.referer:https://www.yemeksepeti.com/}") String referer,
            @Value("${yemeksepeti.geocoder.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36}") String userAgent,
            @Value("${yemeksepeti.geocoder.x-caller-country:tr}") String callerCountry,
            @Value("${yemeksepeti.geocoder.x-caller-platform:b2c}") String callerPlatform,
            @Value("${yemeksepeti.geocoder.x-global-entity-id:YS_TR}") String globalEntityId,
            @Value("${yemeksepeti.geocoder.x-pd-language-id:2}") String pdLanguageId
    ) {
        this.httpClient = httpClient;
        this.reverseUrl = reverseUrl;
        this.language = language;
        this.acceptLanguage = acceptLanguage;
        this.authorization = authorization;
        this.custCode = custCode;
        this.perseusClientId = perseusClientId;
        this.perseusSessionId = perseusSessionId;
        this.origin = origin;
        this.referer = referer;
        this.userAgent = userAgent;
        this.callerCountry = callerCountry;
        this.callerPlatform = callerPlatform;
        this.globalEntityId = globalEntityId;
        this.pdLanguageId = pdLanguageId;
    }

    public String reverse(YemeksepetiReverseGeocodeRequest request) {
        String url = UriComponentsBuilder
                .fromUriString(reverseUrl)
                .queryParam("lat", request.latitude())
                .queryParam("lng", request.longitude())
                .queryParam("language", language)
                .build(true)
                .toUriString();

        Request httpRequest = new Request.Builder()
                .url(url)
                .get()
                .header(HttpHeaders.ACCEPT, "application/json")
                .header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header(HEADER_CUST_CODE, custCode)
                .header(HttpHeaders.ORIGIN, origin)
                .header(HEADER_PERSEUS_CLIENT_ID, perseusClientId)
                .header(HEADER_PERSEUS_SESSION_ID, perseusSessionId)
                .header(HttpHeaders.REFERER, referer)
                .header(HttpHeaders.USER_AGENT, userAgent)
                .header(HEADER_X_CALLER_COUNTRY, callerCountry)
                .header(HEADER_X_CALLER_PLATFORM, callerPlatform)
                .header(HEADER_X_GLOBAL_ENTITY_ID, globalEntityId)
                .header(HEADER_X_PD_LANGUAGE_ID, pdLanguageId)
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (response.body() == null) {
                throw new SPException(
                        HttpStatus.BAD_GATEWAY,
                        "YEMEKSEPETI_GEOCODER_EMPTY_RESPONSE",
                        "Yemeksepeti geocoder bos yanit dondu."
                );
            }

            String body = response.body().string();
            if (!response.isSuccessful()) {
                throw new SPException(
                        HttpStatus.BAD_GATEWAY,
                        "YEMEKSEPETI_GEOCODER_HTTP_" + response.code(),
                        body
                );
            }
            return body;
        } catch (IOException exception) {
            throw new SPException(
                    HttpStatus.BAD_GATEWAY,
                    "YEMEKSEPETI_GEOCODER_REQUEST_FAILED",
                    exception.getMessage()
            );
        }
    }

    private static String buildHeaderName(String... parts) {
        return String.join("-", parts);
    }
}
