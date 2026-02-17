package com.mustafabulu.smartpantry.yemeksepeti.model;

public record YemeksepetiProductDetails(String name, double currentPrice, double originalPrice, int stockAmount,
                                        String campaignEndTime, String netAmount,
                                        double priceMultiplier, String unit, Integer unitValue) {

    public double getAdjustedPrice() {
        if (priceMultiplier <= 0) {
            return currentPrice;
        }
        return currentPrice * priceMultiplier;
    }

}
