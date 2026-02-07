package com.mustafabulu.smartpantry.dto;

import jakarta.validation.constraints.NotBlank;

public record MarketplaceProductRequest(@NotBlank String productId) {
}
