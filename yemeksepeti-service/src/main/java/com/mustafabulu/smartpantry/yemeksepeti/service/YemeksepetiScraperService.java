package com.mustafabulu.smartpantry.yemeksepeti.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.smartpantry.common.core.response.ResponseMessages;
import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.yemeksepeti.constant.YemeksepetiScraperConstants;
import com.mustafabulu.smartpantry.yemeksepeti.model.YemeksepetiProductDetails;
import lombok.AllArgsConstructor;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Service
@AllArgsConstructor
public class YemeksepetiScraperService {

    private static final int MAX_RETRIES = 20;
    private static final long RETRY_SLEEP_MS = 500L;
    private final Supplier<WebDriver> driverSupplier;

    public YemeksepetiScraperService() {
        this(() -> new ChromeDriver(buildOptions()));
    }

    public YemeksepetiProductDetails fetchProductDetails(String url) {
        WebDriver driver = null;

        try {
            driver = driverSupplier.get();
            driver.get(url);
            String jsonString = waitForPreloadedState(driver);
            if (jsonString == null) {
                return null;
            }

            JsonNode product = parseProduct(jsonString);
            if (product.isMissingNode()) {
                return null;
            }

            return mapProduct(product);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SPException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    String.format(ResponseMessages.YEMEKSEPETI_FETCH_FAILED, url),
                    ResponseMessages.YEMEKSEPETI_FETCH_FAILED_CODE
            );
        } catch (Exception e) {
            throw new SPException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    String.format(ResponseMessages.YEMEKSEPETI_FETCH_FAILED, url),
                    ResponseMessages.YEMEKSEPETI_FETCH_FAILED_CODE
            );
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    private static ChromeOptions buildOptions() {
        ChromeOptions options = new ChromeOptions();
        String chromeBin = System.getenv("CHROME_BIN");
        if (chromeBin != null && !chromeBin.isBlank()) {
            options.setBinary(chromeBin);
        }
        Map<String, Object> prefs = new HashMap<>();
        prefs.put(YemeksepetiScraperConstants.PREFS_IMAGES_KEY, YemeksepetiScraperConstants.PREFS_IMAGES_DISABLED);
        options.setExperimentalOption("prefs", prefs);
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        options.addArguments(YemeksepetiScraperConstants.HEADLESS_ARG);
        options.addArguments(YemeksepetiScraperConstants.DISABLE_BLINK_ARG);
        options.addArguments(YemeksepetiScraperConstants.NO_SANDBOX_ARG);
        options.addArguments(YemeksepetiScraperConstants.DISABLE_DEV_SHM_ARG);
        options.addArguments(
                YemeksepetiScraperConstants.USER_AGENT_ARG_PREFIX + YemeksepetiScraperConstants.USER_AGENT
        );
        return options;
    }

    private String waitForPreloadedState(WebDriver driver) throws InterruptedException {
        for (int i = 0; i < MAX_RETRIES; i++) {
            String htmlSource = driver.getPageSource();
            if (htmlSource != null && htmlSource.contains(YemeksepetiScraperConstants.PRELOADED_KEY)) {
                return extractJson(htmlSource);
            }
            Thread.sleep(RETRY_SLEEP_MS);
        }
        return null;
    }

    private String extractJson(String htmlSource) {
        int start = htmlSource.indexOf(YemeksepetiScraperConstants.PRELOADED_KEY)
                + YemeksepetiScraperConstants.PRELOADED_KEY.length();
        String remainder = htmlSource.substring(start);
        int end = remainder.indexOf("</script>");
        String jsonString = remainder.substring(0, end).trim();
        if (jsonString.endsWith(";")) {
            jsonString = jsonString.substring(0, jsonString.length() - 1);
        }
        return jsonString.replace(":undefined", ":null");
    }

    private JsonNode parseProduct(String jsonString) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonString);
        return root.path(YemeksepetiScraperConstants.PAGE_KEY)
                .path(YemeksepetiScraperConstants.PRODUCT_DETAIL_KEY)
                .path(YemeksepetiScraperConstants.PRODUCT_KEY);
    }

    private YemeksepetiProductDetails mapProduct(JsonNode product) {
        String name = product.path(YemeksepetiScraperConstants.NAME_KEY).asText();
        double currentPrice = product.path(YemeksepetiScraperConstants.PRICE_KEY).asDouble();

        double originalPrice = product.path(YemeksepetiScraperConstants.ORIGINAL_PRICE_KEY).asDouble();
        double strikethroughPrice = product.path(YemeksepetiScraperConstants.STRIKETHROUGH_PRICE_KEY).asDouble();
        double resolvedOriginal = (originalPrice > 0) ? originalPrice : strikethroughPrice;

        int stockAmount = product.path(YemeksepetiScraperConstants.STOCK_AMOUNT_KEY).asInt();

        JsonNode campaigns = product.path(YemeksepetiScraperConstants.ACTIVE_CAMPAIGNS_KEY);
        String campaignEndTime = "";
        if (campaigns.isArray() && !campaigns.isEmpty()) {
            campaignEndTime = campaigns.get(0).path(YemeksepetiScraperConstants.END_TIME_KEY).asText();
        }

        NetAmountInfo netAmountInfo = extractNetAmount(resolveProductInfos(product));
        if (netAmountInfo.unit() == null && netAmountInfo.unitValue() == null) {
            netAmountInfo = extractNetAmountFromAttributes(product);
        }

        return new YemeksepetiProductDetails(
                name,
                currentPrice,
                resolvedOriginal,
                stockAmount,
                campaignEndTime,
                netAmountInfo.netAmount(),
                netAmountInfo.priceMultiplier(),
                netAmountInfo.unit(),
                netAmountInfo.unitValue()
        );
    }

    private JsonNode resolveProductInfos(JsonNode product) {
        JsonNode productInfos = product.path(YemeksepetiScraperConstants.PRODUCT_INFOS_KEY);
        JsonNode infoContainer = product.path(YemeksepetiScraperConstants.FOOD_LABELLING_KEY);
        JsonNode nestedInfos = infoContainer.path(YemeksepetiScraperConstants.PRODUCT_INFOS_KEY);
        if (nestedInfos.isArray()) {
            return nestedInfos;
        }
        JsonNode details = product.path(YemeksepetiScraperConstants.DETAILS_KEY);
        JsonNode detailsInfos = details.path(YemeksepetiScraperConstants.PRODUCT_INFOS_KEY);
        if (detailsInfos.isArray()) {
            return detailsInfos;
        }
        JsonNode infoList = product.path(YemeksepetiScraperConstants.INFO_LIST_KEY);
        JsonNode infoListInfos = infoList.path(YemeksepetiScraperConstants.PRODUCT_INFOS_KEY);
        if (infoListInfos.isArray()) {
            return infoListInfos;
        }
        return productInfos;
    }

    private NetAmountInfo extractNetAmount(JsonNode productInfos) {
        if (productInfos == null || !productInfos.isArray()) {
            return new NetAmountInfo("", 1, null, null);
        }

        for (JsonNode info : productInfos) {
            String title = info.path(YemeksepetiScraperConstants.TITLE_KEY).asText("");
            String normalizedTitle = title.trim().toLowerCase(java.util.Locale.ROOT);
            if (!normalizedTitle.contains(YemeksepetiScraperConstants.NET_MIKTAR_LABEL)) {
                continue;
            }
            JsonNode values = info.path(YemeksepetiScraperConstants.VALUES_KEY);
            if (!values.isArray() || values.isEmpty()) {
                return new NetAmountInfo("", 1, null, null);
            }

            String netAmount = values.get(0).asText("");
            AmountInfo amountInfo = parseAmountInfo(netAmount);
            return new NetAmountInfo(
                    netAmount,
                    amountInfo.multiplier(),
                    amountInfo.unit(),
                    amountInfo.unitValue()
            );
        }

        return new NetAmountInfo("", 1, null, null);
    }

    private NetAmountInfo extractNetAmountFromAttributes(JsonNode product) {
        JsonNode attributes = product.path(YemeksepetiScraperConstants.ATTRIBUTES_KEY);
        if (attributes.isMissingNode()) {
            return new NetAmountInfo("", 1, null, null);
        }
        double baseContentValue = attributes.path(YemeksepetiScraperConstants.BASE_CONTENT_VALUE_KEY).asDouble(0);
        String baseUnit = attributes.path(YemeksepetiScraperConstants.BASE_UNIT_KEY).asText("");
        if (baseContentValue <= 0 || baseUnit.isBlank()) {
            return new NetAmountInfo("", 1, null, null);
        }
        String netAmount = baseContentValue + baseUnit;
        AmountInfo amountInfo = parseAmountInfo(netAmount);
        return new NetAmountInfo(
                netAmount,
                amountInfo.multiplier(),
                amountInfo.unit(),
                amountInfo.unitValue()
        );
    }

    private AmountInfo parseAmountInfo(String netAmount) {
        if (netAmount == null || netAmount.isBlank()) {
            return new AmountInfo(1, null, null);
        }

        String normalized = netAmount.trim().toLowerCase();
        double value = parseAmountValue(normalized);
        if (value <= 0) {
            return new AmountInfo(1, null, null);
        }

        if (normalized.contains("kg")) {
            double grams = value * 1000;
            double multiplier = grams < 1000 ? 1000 / grams : 1;
            return new AmountInfo(multiplier, "g", (int) Math.round(grams));
        }
        if (normalized.contains("g")) {
            double multiplier = value < 1000 ? 1000 / value : 1;
            return new AmountInfo(multiplier, "g", (int) Math.round(value));
        }
        if (normalized.contains("ml")) {
            double multiplier = value < 1000 ? 1000 / value : 1;
            return new AmountInfo(multiplier, "ml", (int) Math.round(value));
        }
        if (normalized.contains("l")) {
            double ml = value * 1000;
            double multiplier = ml < 1000 ? 1000 / ml : 1;
            return new AmountInfo(multiplier, "ml", (int) Math.round(ml));
        }

        return new AmountInfo(1, null, null);
    }

    private double parseAmountValue(String netAmount) {
        String number = netAmount.replaceAll("[^0-9.,]", "");
        if (number.isBlank()) {
            return 0;
        }
        number = number.replace(",", ".");
        try {
            return Double.parseDouble(number);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private record NetAmountInfo(String netAmount, double priceMultiplier, String unit, Integer unitValue) {
    }

    private record AmountInfo(double multiplier, String unit, Integer unitValue) {
    }
}
