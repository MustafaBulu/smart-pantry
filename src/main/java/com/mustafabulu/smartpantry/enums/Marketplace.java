package com.mustafabulu.smartpantry.enums;

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
        for (Marketplace marketplace : values()) {
            if (marketplace.code.equals(normalized)) {
                return marketplace;
            }
        }
        return null;
    }
}
