package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.enums.Marketplace;

import java.util.List;

public interface MarketplaceCategorySeedService {

    Marketplace marketplace();

    List<String> listSeedCategoryKeys();
}
