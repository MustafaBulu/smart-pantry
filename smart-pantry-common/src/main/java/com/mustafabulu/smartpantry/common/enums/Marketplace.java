package com.mustafabulu.smartpantry.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Marketplace {
    YS("YS", "Yemeksepeti"),
    MG("MG", "Migros");

    private final String code;
    private final String displayName;


    public static Marketplace fromCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        if ("YEMEKSEPETI".equals(normalized) || "YEMEKSEPETİ".equals(normalized)) {
            normalized = "YS";
        } else if ("MIGROS".equals(normalized) || "MİGROS".equals(normalized)) {
            normalized = "MG";
        }
        for (Marketplace marketplace : values()) {
            if (marketplace.code.equals(normalized)) {
                return marketplace;
            }
        }
        return null;
    }
}
