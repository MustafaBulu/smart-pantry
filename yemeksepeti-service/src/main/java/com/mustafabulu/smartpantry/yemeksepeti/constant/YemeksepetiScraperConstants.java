package com.mustafabulu.smartpantry.yemeksepeti.constant;

public final class YemeksepetiScraperConstants {

    public static final String PRELOADED_KEY = "window.__PRELOADED_STATE__=";
    public static final String PREFS_IMAGES_KEY = "profile.managed_default_content_settings.images";
    public static final int PREFS_IMAGES_DISABLED = 2;
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    public static final String HEADLESS_ARG = "--headless=new";
    public static final String DISABLE_BLINK_ARG = "--disable-blink-features=AutomationControlled";
    public static final String NO_SANDBOX_ARG = "--no-sandbox";
    public static final String DISABLE_DEV_SHM_ARG = "--disable-dev-shm-usage";
    public static final String PAGE_KEY = "page";
    public static final String PRODUCT_DETAIL_KEY = "productDetail";
    public static final String PRODUCT_KEY = "product";
    public static final String PRODUCT_INFOS_KEY = "productInfos";
    public static final String FOOD_LABELLING_KEY = "foodLabelling";
    public static final String DETAILS_KEY = "details";
    public static final String INFO_LIST_KEY = "infoList";
    public static final String TITLE_KEY = "title";
    public static final String VALUES_KEY = "values";
    public static final String NET_MIKTAR_LABEL = "net miktar";
    public static final String ATTRIBUTES_KEY = "attributes";
    public static final String BASE_CONTENT_VALUE_KEY = "baseContentValue";
    public static final String BASE_UNIT_KEY = "baseUnit";
    public static final String NAME_KEY = "name";
    public static final String PRICE_KEY = "price";
    public static final String ORIGINAL_PRICE_KEY = "originalPrice";
    public static final String STRIKETHROUGH_PRICE_KEY = "strikethroughPrice";
    public static final String STOCK_AMOUNT_KEY = "stockAmount";
    public static final String ACTIVE_CAMPAIGNS_KEY = "activeCampaigns";
    public static final String END_TIME_KEY = "endTime";
    public static final String USER_AGENT_ARG_PREFIX = "--user-agent=";

    private YemeksepetiScraperConstants() {
    }
}
