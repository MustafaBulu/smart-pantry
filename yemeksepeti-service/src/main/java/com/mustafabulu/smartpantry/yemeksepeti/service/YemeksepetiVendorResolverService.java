package com.mustafabulu.smartpantry.yemeksepeti.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.smartpantry.common.config.MarketplaceUrlProperties;
import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.common.dto.request.YemeksepetiReverseGeocodeRequest;
import com.mustafabulu.smartpantry.common.dto.response.YemeksepetiVendorByLocationResponse;
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
public class YemeksepetiVendorResolverService {
    private static final String HEADER_PERSEUS_CLIENT_ID = buildHeaderName("perseus", "client", "id");
    private static final String HEADER_PERSEUS_SESSION_ID = buildHeaderName("perseus", "session", "id");
    private static final String HEADER_X_PD_LANGUAGE_ID = buildHeaderName("X", "PD", "Language", "ID");
    private static final String HEADER_X_FP_API_KEY = buildHeaderName("X", "FP", "API", "KEY");
    private static final String HEADER_X_DISCO_CLIENT_ID = buildHeaderName("x", "disco", "client", "id");
    private static final String YEMEKSEPETI_RESTAURANT_BASE = "https://yemeksepeti.com/restaurant/";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final String vendorsUrl;
    private final String chainCode;
    private final String country;
    private final String customerType;
    private final String acceptLanguage;
    private final String referer;
    private final String perseusClientId;
    private final String perseusSessionId;
    private final String pdLanguageId;
    private final String fpApiKey;
    private final String discoClientId;
    private final String origin;
    private final String userAgent;
    private final MarketplaceUrlProperties marketplaceUrlProperties;
    private final String yemeksepetiBaseFallback;

    public YemeksepetiVendorResolverService(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${yemeksepeti.vendor-resolver.url:https://tr.fd-api.com/vendors-gateway/api/v1/pandora/vendors}") String vendorsUrl,
            @Value("${yemeksepeti.vendor-resolver.chain-code:ct4lp}") String chainCode,
            @Value("${yemeksepeti.vendor-resolver.country:tr}") String country,
            @Value("${yemeksepeti.vendor-resolver.customer-type:regular}") String customerType,
            @Value("${yemeksepeti.vendor-resolver.accept-language:tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7}") String acceptLanguage,
            @Value("${yemeksepeti.vendor-resolver.referer:https://www.yemeksepeti.com/}") String referer,
            @Value("${yemeksepeti.vendor-resolver.perseus-client-id:}") String perseusClientId,
            @Value("${yemeksepeti.vendor-resolver.perseus-session-id:}") String perseusSessionId,
            @Value("${yemeksepeti.vendor-resolver.x-pd-language-id:2}") String pdLanguageId,
            @Value("${yemeksepeti.vendor-resolver.x-fp-api-key:}") String fpApiKey,
            @Value("${yemeksepeti.vendor-resolver.x-disco-client-id:}") String discoClientId,
            @Value("${yemeksepeti.vendor-resolver.origin:https://www.yemeksepeti.com}") String origin,
            @Value("${yemeksepeti.vendor-resolver.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0}") String userAgent,
            @Value("${marketplace.urls.yemeksepeti-base:${YEMEKSEPETI_BASE_URL:}}") String yemeksepetiBaseFallback,
            MarketplaceUrlProperties marketplaceUrlProperties
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.vendorsUrl = vendorsUrl;
        this.chainCode = chainCode;
        this.country = country;
        this.customerType = customerType;
        this.acceptLanguage = acceptLanguage;
        this.referer = referer;
        this.perseusClientId = perseusClientId;
        this.perseusSessionId = perseusSessionId;
        this.pdLanguageId = pdLanguageId;
        this.fpApiKey = fpApiKey;
        this.discoClientId = discoClientId;
        this.origin = origin;
        this.userAgent = userAgent;
        this.yemeksepetiBaseFallback = yemeksepetiBaseFallback;
        this.marketplaceUrlProperties = marketplaceUrlProperties;
    }

    public YemeksepetiVendorByLocationResponse resolve(YemeksepetiReverseGeocodeRequest request) {
        String url = buildResolveUrl(request);
        Request httpRequest = buildResolveRequest(url);

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String body = readResponseBodyOrThrow(response);
            if (!response.isSuccessful()) {
                YemeksepetiVendorByLocationResponse fallback = resolveFallbackFrom403(response.code());
                if (fallback != null) {
                    return fallback;
                }
                throw new SPException(
                        HttpStatus.BAD_GATEWAY,
                        "YEMEKSEPETI_VENDOR_HTTP_" + response.code(),
                        body
                );
            }
            return parseAndPersistVendorResponse(body);
        } catch (IOException exception) {
            throw new SPException(
                    HttpStatus.BAD_GATEWAY,
                    "YEMEKSEPETI_VENDOR_REQUEST_FAILED",
                    exception.getMessage()
            );
        }
    }

    private String buildResolveUrl(YemeksepetiReverseGeocodeRequest request) {
        return UriComponentsBuilder
                .fromUriString(vendorsUrl)
                .queryParam("chain_code", chainCode)
                .queryParam("country", country)
                .queryParam("customer_type", customerType)
                .queryParam("latitude", request.latitude())
                .queryParam("longitude", request.longitude())
                .build(true)
                .toUriString();
    }

    private Request buildResolveRequest(String url) {
        return new Request.Builder()
                .url(url)
                .get()
                .header(HttpHeaders.ACCEPT, "application/json, text/plain, */*")
                .header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage)
                .header(HttpHeaders.REFERER, referer)
                .header(HEADER_PERSEUS_CLIENT_ID, perseusClientId)
                .header(HEADER_PERSEUS_SESSION_ID, perseusSessionId)
                .header(HEADER_X_PD_LANGUAGE_ID, pdLanguageId)
                .header(HEADER_X_FP_API_KEY, fpApiKey)
                .header(HEADER_X_DISCO_CLIENT_ID, discoClientId)
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.USER_AGENT, userAgent)
                .build();
    }

    private String readResponseBodyOrThrow(Response response) throws IOException {
        if (response.body() == null) {
            throw new SPException(
                    HttpStatus.BAD_GATEWAY,
                    "YEMEKSEPETI_VENDOR_EMPTY_RESPONSE",
                    "Yemeksepeti vendor endpoint bos yanit dondu."
            );
        }
        return response.body().string();
    }

    private YemeksepetiVendorByLocationResponse resolveFallbackFrom403(int statusCode) {
        if (statusCode != 403) {
            return null;
        }
        String fallbackBaseUrl = marketplaceUrlProperties.getYemeksepetiBase();
        if (fallbackBaseUrl == null || fallbackBaseUrl.isBlank()) {
            fallbackBaseUrl = yemeksepetiBaseFallback;
        }
        if (fallbackBaseUrl == null || fallbackBaseUrl.isBlank()) {
            return null;
        }
        return new YemeksepetiVendorByLocationResponse(fallbackBaseUrl);
    }

    private YemeksepetiVendorByLocationResponse parseAndPersistVendorResponse(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode items = root.path("data").path("items");
        if (!items.isArray() || items.isEmpty()) {
            throw new SPException(
                    HttpStatus.NOT_FOUND,
                    "YEMEKSEPETI_VENDOR_NOT_FOUND",
                    body
            );
        }

        JsonNode selected = items.get(0);
        String vendorCode = resolveVendorCode(selected, body);
        String redirectionUrl = resolveRedirectionUrl(selected, vendorCode);
        marketplaceUrlProperties.setYemeksepetiBase(redirectionUrl);
        return new YemeksepetiVendorByLocationResponse(redirectionUrl);
    }

    private String resolveVendorCode(JsonNode selected, String body) {
        String code = selected.path("code").asText(null);
        if (code == null || code.isBlank()) {
            code = selected.path("vendor_code").asText(null);
        }
        if (code == null || code.isBlank()) {
            throw new SPException(
                    HttpStatus.BAD_GATEWAY,
                    "YEMEKSEPETI_VENDOR_CODE_MISSING",
                    body
            );
        }
        return code;
    }

    private String resolveRedirectionUrl(JsonNode selected, String vendorCode) {
        String redirectionUrl = selected.path("redirection_url").asText(null);
        if (redirectionUrl == null || redirectionUrl.isBlank()) {
            return YEMEKSEPETI_RESTAURANT_BASE + vendorCode;
        }
        return redirectionUrl;
    }

    private static String buildHeaderName(String... parts) {
        return String.join("-", parts);
    }
}
