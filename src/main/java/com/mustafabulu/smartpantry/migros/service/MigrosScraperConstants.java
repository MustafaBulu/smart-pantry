package com.mustafabulu.smartpantry.migros.service;

public final class MigrosScraperConstants {

    public static final String USER_AGENT_HEADER = "User-Agent";
    public static final String USER_AGENT_VALUE = "Mozilla/5.0";
    public static final String SUCCESSFUL_KEY = "successful";
    public static final String DATA_KEY = "data";
    public static final String STORE_PRODUCT_INFO_KEY = "storeProductInfoDTO";
    public static final String NAME_KEY = "name";
    public static final String DESCRIPTION_KEY = "description";
    public static final String BRAND_KEY = "brand";
    public static final String BRAND_NAME_KEY = "name";
    public static final String SALE_PRICE_KEY = "salePrice";
    public static final String PRICE_KEY = "price";
    public static final String DISCOUNT_PRICE_KEY = "discountPrice";
    public static final String ORIGINAL_PRICE_KEY = "originalPrice";
    public static final String LIST_PRICE_KEY = "listPrice";
    public static final String NET_MIKTAR_REGEX =
            "Net Miktar[^<]*</strong>\\s*<br>\\s*([^<]+)";

    private MigrosScraperConstants() {
    }
}
