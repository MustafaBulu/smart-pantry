package com.mustafabulu.smartpantry.migros.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.common.dto.request.MigrosStoreIdByLocationRequest;
import com.mustafabulu.smartpantry.common.dto.response.MigrosStoreIdByLocationResponse;
import com.mustafabulu.smartpantry.migros.constant.MigrosConstants;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@ConditionalOnProperty(prefix = "marketplace.mg", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigrosStoreIdResolverService {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final String migrosDeliveryUrl;
    private final String migrosCheckoutStoreLookupUrl;
    private final String migrosCartUrl;
    private final String migrosCartStoreIdUrl;
    private final String migrosDeliveryCookie;
    private final String migrosDeliveryReferer;
    private final boolean migrosDeliveryHeaderForwardedRest;
    private final boolean migrosDeliveryHeaderPwa;
    private final boolean migrosDeliveryHeaderDevicePwa;
    private final String migrosLocationsBaseUrl;
    private final MigrosCookieSessionService migrosCookieSessionService;
    private final boolean migrosStoreIdCacheEnabled;
    private final Duration migrosStoreIdCacheTtl;
    private final int migrosStoreIdCacheCoordinateScale;
    private final ConcurrentMap<CoordinateKey, CachedStoreId> storeIdCache = new ConcurrentHashMap<>();

    public MigrosStoreIdResolverService(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${migros.delivery.url:https://www.migros.com.tr/rest/delivery-bff/v2/delivery-addresses}") String migrosDeliveryUrl,
            @Value("${migros.delivery.checkout-lookup-url:https://www.migros.com.tr/rest/delivery-bff/delivery-addresses/checkouts}") String migrosCheckoutStoreLookupUrl,
            @Value("${migros.cart.url:https://www.migros.com.tr/rest/carts/screens/V2?reid=1771310814233000001}") String migrosCartUrl,
            @Value("${migros.cart.store-id-url:https://www.migros.com.tr/rest/carts?isCartPage=false}") String migrosCartStoreIdUrl,
            @Value("${migros.delivery.cookie:}") String migrosDeliveryCookie,
            @Value("${migros.cart.cookie:}") String migrosCartCookie,
            @Value("${migros.delivery.referer:https://www.migros.com.tr/}") String migrosDeliveryReferer,
            @Value("${migros.delivery.header.forwarded-rest:true}") boolean migrosDeliveryHeaderForwardedRest,
            @Value("${migros.delivery.header.pwa:true}") boolean migrosDeliveryHeaderPwa,
            @Value("${migros.delivery.header.device-pwa:true}") boolean migrosDeliveryHeaderDevicePwa,
            @Value("${migros.locations.base-url:https://www.migros.com.tr/rest/delivery-bff/locations}") String migrosLocationsBaseUrl,
            @Value("${migros.store-id-cache.enabled:true}") boolean migrosStoreIdCacheEnabled,
            @Value("${migros.store-id-cache.ttl:PT24H}") Duration migrosStoreIdCacheTtl,
            @Value("${migros.store-id-cache.coordinate-scale:6}") int migrosStoreIdCacheCoordinateScale,
            MigrosCookieSessionService migrosCookieSessionService
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.migrosDeliveryUrl = migrosDeliveryUrl;
        this.migrosCheckoutStoreLookupUrl = migrosCheckoutStoreLookupUrl;
        this.migrosCartUrl = migrosCartUrl;
        this.migrosCartStoreIdUrl = migrosCartStoreIdUrl;
        this.migrosDeliveryCookie = (migrosDeliveryCookie == null || migrosDeliveryCookie.isBlank())
                ? migrosCartCookie
                : migrosDeliveryCookie;
        this.migrosDeliveryReferer = migrosDeliveryReferer;
        this.migrosDeliveryHeaderForwardedRest = migrosDeliveryHeaderForwardedRest;
        this.migrosDeliveryHeaderPwa = migrosDeliveryHeaderPwa;
        this.migrosDeliveryHeaderDevicePwa = migrosDeliveryHeaderDevicePwa;
        this.migrosLocationsBaseUrl = migrosLocationsBaseUrl;
        this.migrosStoreIdCacheEnabled = migrosStoreIdCacheEnabled;
        this.migrosStoreIdCacheTtl = migrosStoreIdCacheTtl;
        this.migrosStoreIdCacheCoordinateScale = migrosStoreIdCacheCoordinateScale;
        this.migrosCookieSessionService = migrosCookieSessionService;
    }

    public MigrosStoreIdByLocationResponse resolveStoreId(MigrosStoreIdByLocationRequest request) {
        CoordinateKey cacheKey = toCoordinateKey(request.latitude(), request.longitude());
        MigrosStoreIdByLocationResponse cachedResponse = getCachedStoreId(cacheKey);
        if (cachedResponse != null) {
            return cachedResponse;
        }

        String cookieToUse = resolveCookieOrThrow();
        MigrosStoreIdByLocationResponse fallbackResult = resolveStoreIdUsingFallbackEndpoints(request, cookieToUse);
        if (fallbackResult != null) {
            cacheStoreId(cacheKey, fallbackResult.storeId());
            return fallbackResult;
        }

        LocationNode city = findNearestLocation(fetchLocations("/cities"), request.latitude(), request.longitude(), "city");
        LocationNode town = findNearestLocation(fetchLocations("/towns/" + city.id()), request.latitude(), request.longitude(), "town");
        LocationNode district = findNearestLocation(fetchLocations("/districts/" + town.id()), request.latitude(), request.longitude(), "district");

        JsonNode deliveryResponse = callDeliveryAddressEndpoint(request, city, town, district, cookieToUse);
        long storeId = readRequiredStoreId(deliveryResponse);
        cacheStoreId(cacheKey, storeId);
        return new MigrosStoreIdByLocationResponse(storeId);
    }

    private String resolveCookieOrThrow() {
        String cookieToUse = migrosCookieSessionService.resolveCookie(migrosDeliveryCookie);
        if (cookieToUse == null || cookieToUse.isBlank()) {
            try {
                migrosCookieSessionService.refreshFromSelenium();
            } catch (Exception ignored) {
                cookieToUse = migrosDeliveryCookie;
            }
            if (cookieToUse == null || cookieToUse.isBlank()) {
                cookieToUse = migrosCookieSessionService.resolveCookie(migrosDeliveryCookie);
            }
        }
        if (cookieToUse == null || cookieToUse.isBlank()) {
            throw new SPException(
                    HttpStatus.BAD_REQUEST,
                    "MIGROS_DELIVERY_COOKIE_REQUIRED",
                    "Migros delivery cookie bos olamaz."
            );
        }
        return cookieToUse;
    }

    private MigrosStoreIdByLocationResponse resolveStoreIdUsingFallbackEndpoints(
            MigrosStoreIdByLocationRequest request,
            String cookieHeader
    ) {
        MigrosStoreIdByLocationResponse checkoutLookupResult = tryResolveStoreIdFromCheckoutLookup(request, cookieHeader);
        if (checkoutLookupResult != null) {
            return checkoutLookupResult;
        }
        return tryResolveStoreIdFromCartSnapshot(cookieHeader);
    }

    private MigrosStoreIdByLocationResponse tryResolveStoreIdFromCheckoutLookup(
            MigrosStoreIdByLocationRequest request,
            String cookieHeader
    ) {
        Long checkoutId = fetchCheckoutId(cookieHeader);
        if (checkoutId == null) {
            return null;
        }

        String url = UriComponentsBuilder
                .fromUriString(migrosCheckoutStoreLookupUrl)
                .queryParam("checkoutId", checkoutId)
                .queryParam("latitude", request.latitude())
                .queryParam("longitude", request.longitude())
                .build(true)
                .toUriString();

        Request httpRequest = new Request.Builder()
                .url(url)
                .get()
                .headers(buildDefaultHeaders(cookieHeader, true, true, false))
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            Long storeId = readFirstLong(
                    root,
                    MigrosConstants.STORE_ID_FIELD,
                    "storeInfo.id",
                    "selectedDeliveryAddressInfo." + MigrosConstants.STORE_ID_FIELD,
                    "addressInfo.storeId"
            );
            if (storeId == null) {
                return null;
            }
            return new MigrosStoreIdByLocationResponse(storeId);
        } catch (IOException ignored) {
            return null;
        }
    }

    private Long fetchCheckoutId(String cookieHeader) {
        Request request = new Request.Builder()
                .url(migrosCartUrl)
                .get()
                .headers(buildDefaultHeaders(cookieHeader, false, true, false))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            return readFirstLong(
                    root,
                    "data.cartInfo.line.id",
                    "data.cartInfoDTO.line.id",
                    "data.line.id",
                    "checkoutId"
            );
        } catch (IOException ignored) {
            return null;
        }
    }

    private MigrosStoreIdByLocationResponse tryResolveStoreIdFromCartSnapshot(String cookieHeader) {
        String url = UriComponentsBuilder
                .fromUriString(migrosCartStoreIdUrl)
                .queryParam("reid", System.currentTimeMillis())
                .build(true)
                .toUriString();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .headers(buildDefaultHeaders(cookieHeader, true, true, false))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            Long storeId = readFirstLong(
                    root,
                    MigrosConstants.STORE_ID_FIELD,
                    "data." + MigrosConstants.STORE_ID_FIELD,
                    "data.cartInfo.line.storeId",
                    "data.cartInfoDTO.line.storeId",
                    "data.line.storeId"
            );
            if (storeId == null) {
                return null;
            }
            return new MigrosStoreIdByLocationResponse(storeId);
        } catch (IOException ignored) {
            return null;
        }
    }

    private JsonNode callDeliveryAddressEndpoint(
            MigrosStoreIdByLocationRequest request,
            LocationNode city,
            LocationNode town,
            LocationNode district,
            String cookieHeader
    ) {
        String reid = String.valueOf(System.currentTimeMillis());
        String url = UriComponentsBuilder
                .fromUriString(migrosDeliveryUrl)
                .queryParam("reid", reid)
                .build(true)
                .toUriString();

        String payload = """
                {
                  "addressRequest": {
                    "name": "konum",
                    "districtId": %d,
                    "firstName": "smart",
                    "lastName": "pantry",
                    "townId": %d,
                    "cityId": %d,
                    "streetName": "konum",
                    "floorNumber": "1",
                    "doorNumber": "1",
                    "buildingNumber": "1",
                    "direction": "konum",
                    "latitude": %s,
                    "longitude": %s,
                    "detail": "%s, %s",
                    "townName": "%s",
                    "districtName": "%s"
                  }
                }
                """.formatted(
                district.id(),
                town.id(),
                city.id(),
                request.latitude(),
                request.longitude(),
                escapeJson(district.name()),
                escapeJson(town.name()),
                escapeJson(town.name()),
                escapeJson(district.name())
        );

        Request httpRequest = new Request.Builder()
                .url(url)
                .post(RequestBody.create(payload, JSON_MEDIA_TYPE))
                .headers(buildDefaultHeaders(cookieHeader, true, true, true))
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (response.body() == null) {
                throw new SPException(
                        HttpStatus.BAD_GATEWAY,
                        "MIGROS_DELIVERY_EMPTY_RESPONSE",
                        "Migros delivery endpoint bos yanit dondu."
                );
            }
            String body = response.body().string();
            if (!response.isSuccessful()) {
                throw new SPException(
                        HttpStatus.BAD_GATEWAY,
                        "MIGROS_DELIVERY_HTTP_" + response.code(),
                        body
                );
            }
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new SPException(
                    HttpStatus.BAD_GATEWAY,
                    "MIGROS_DELIVERY_REQUEST_FAILED",
                    exception.getMessage()
            );
        }
    }

    private List<LocationNode> fetchLocations(String path) {
        String url = migrosLocationsBaseUrl + path;
        Request request = new Request.Builder()
                .url(url)
                .headers(buildDefaultHeaders(null, false, false, false))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new SPException(
                        HttpStatus.BAD_GATEWAY,
                        "MIGROS_LOCATION_HTTP_" + response.code(),
                        "Migros location endpoint hatasi: " + url
                );
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            if (!root.isArray()) {
                throw new SPException(
                        HttpStatus.BAD_GATEWAY,
                        "MIGROS_LOCATION_INVALID_RESPONSE",
                        "Migros location endpoint beklenen formatta donmedi: " + url
                );
            }

            List<LocationNode> locations = new ArrayList<>();
            for (JsonNode node : root) {
                JsonNode latNode = node.path("latitude");
                JsonNode lonNode = node.path("longitude");
                JsonNode idNode = node.path("id");
                JsonNode nameNode = node.path("name");
                if (idNode.isMissingNode() || nameNode.isMissingNode() || !latNode.isNumber() || !lonNode.isNumber()) {
                    continue;
                }
                locations.add(new LocationNode(
                        idNode.asLong(),
                        nameNode.asText(""),
                        latNode.asDouble(),
                        lonNode.asDouble()
                ));
            }
            return locations;
        } catch (IOException exception) {
            throw new SPException(
                    HttpStatus.BAD_GATEWAY,
                    "MIGROS_LOCATION_REQUEST_FAILED",
                    exception.getMessage()
            );
        }
    }

    private LocationNode findNearestLocation(
            List<LocationNode> candidates,
            double latitude,
            double longitude,
            String locationType
    ) {
        if (candidates.isEmpty()) {
            throw new SPException(
                    HttpStatus.BAD_GATEWAY,
                    "MIGROS_" + locationType.toUpperCase() + "_NOT_FOUND",
                    "Migros " + locationType + " listesi bos dondu."
            );
        }
        return candidates.stream()
                .filter(node -> !Double.isNaN(node.latitude()) && !Double.isNaN(node.longitude()))
                .min(Comparator.comparingDouble(node -> distanceKm(latitude, longitude, node.latitude(), node.longitude())))
                .orElseThrow(() -> new SPException(
                        HttpStatus.BAD_GATEWAY,
                        "MIGROS_" + locationType.toUpperCase() + "_NOT_FOUND",
                        "Migros " + locationType + " cozumlenemedi."
                ));
    }

    private long readRequiredStoreId(JsonNode node) {
        JsonNode field = node.path(MigrosConstants.STORE_ID_FIELD);
        if (!field.isNumber()) {
            throw new SPException(
                    HttpStatus.BAD_GATEWAY,
                    "MIGROS_RESPONSE_MISSING_" + MigrosConstants.STORE_ID_FIELD.toUpperCase(),
                    "Migros yanitinda zorunlu alan eksik: " + MigrosConstants.STORE_ID_FIELD
            );
        }
        return field.asLong();
    }

    private okhttp3.Headers buildDefaultHeaders(
            String cookieHeader,
            boolean includeOrigin,
            boolean includeReferer,
            boolean includeContentType
    ) {
        okhttp3.Headers.Builder headersBuilder = new okhttp3.Headers.Builder()
                .add(HttpHeaders.ACCEPT, MigrosConstants.JSON_CONTENT_TYPE)
                .add(MigrosConstants.HEADER_X_FORWARDED_REST, String.valueOf(migrosDeliveryHeaderForwardedRest))
                .add(MigrosConstants.HEADER_X_PWA, String.valueOf(migrosDeliveryHeaderPwa))
                .add(MigrosConstants.HEADER_X_DEVICE_PWA, String.valueOf(migrosDeliveryHeaderDevicePwa));
        if (includeReferer) {
            headersBuilder.add(HttpHeaders.REFERER, migrosDeliveryReferer);
        }
        if (includeOrigin) {
            headersBuilder.add(HttpHeaders.ORIGIN, MigrosConstants.MIGROS_ORIGIN);
        }
        if (includeContentType) {
            headersBuilder.add(HttpHeaders.CONTENT_TYPE, MigrosConstants.JSON_CONTENT_TYPE);
        }
        if (cookieHeader != null && !cookieHeader.isBlank()) {
            headersBuilder.add(HttpHeaders.COOKIE, cookieHeader);
        }
        return headersBuilder.build();
    }

    private Long readFirstLong(JsonNode root, String... paths) {
        for (String path : paths) {
            JsonNode node = resolvePathNode(root, path);
            Long value = toLongValue(node);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private JsonNode resolvePathNode(JsonNode root, String path) {
        JsonNode current = root;
        String[] parts = path.split("\\.");
        for (String part : parts) {
            current = current.path(part);
            if (current.isMissingNode() || current.isNull()) {
                return null;
            }
        }
        return current;
    }

    private Long toLongValue(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        if (!node.isTextual()) {
            return null;
        }
        try {
            return Long.parseLong(node.asText().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private CoordinateKey toCoordinateKey(double latitude, double longitude) {
        return new CoordinateKey(
                normalizeCoordinate(latitude, migrosStoreIdCacheCoordinateScale),
                normalizeCoordinate(longitude, migrosStoreIdCacheCoordinateScale)
        );
    }

    private String normalizeCoordinate(double value, int scale) {
        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private MigrosStoreIdByLocationResponse getCachedStoreId(CoordinateKey key) {
        if (!migrosStoreIdCacheEnabled) {
            return null;
        }
        CachedStoreId cached = storeIdCache.get(key);
        if (cached == null) {
            return null;
        }
        if (cached.isExpired(migrosStoreIdCacheTtl)) {
            storeIdCache.remove(key, cached);
            return null;
        }
        return new MigrosStoreIdByLocationResponse(cached.storeId());
    }

    private void cacheStoreId(CoordinateKey key, Long storeId) {
        if (!migrosStoreIdCacheEnabled || storeId == null) {
            return;
        }
        storeIdCache.put(key, new CachedStoreId(storeId, Instant.now()));
    }

    private record LocationNode(long id, String name, double latitude, double longitude) {
        private LocationNode {
            Objects.requireNonNull(name);
        }
    }

    private record CoordinateKey(String latitude, String longitude) {
    }

    private record CachedStoreId(Long storeId, Instant loadedAt) {
        private boolean isExpired(Duration ttl) {
            return loadedAt.plus(ttl).isBefore(Instant.now());
        }
    }
}
