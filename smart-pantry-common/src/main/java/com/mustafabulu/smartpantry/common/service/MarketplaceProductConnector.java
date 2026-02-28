package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.model.Category;
import com.mustafabulu.smartpantry.common.model.MarketplaceProduct;

public interface MarketplaceProductConnector {

    Marketplace marketplace();

    String buildProductUrl(String externalId);

    boolean recordDetailsForProduct(Category category, MarketplaceProduct marketplaceProduct);
}
