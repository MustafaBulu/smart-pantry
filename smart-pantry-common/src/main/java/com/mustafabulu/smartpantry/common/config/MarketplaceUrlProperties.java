package com.mustafabulu.smartpantry.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "marketplace.urls")
@Getter
@Setter
public class MarketplaceUrlProperties {

    private String yemeksepetiBase;
    private String migrosPrefix;
    private String migrosSuffix;

}
