package com.mustafabulu.smartpantry.dto;

import com.mustafabulu.smartpantry.core.response.ResponseMessages;
import jakarta.validation.constraints.Pattern;

public record ProductSearchRequest(
        @Pattern(regexp = "(?i)^(YS|MG)$", message = ResponseMessages.INVALID_MARKETPLACE_CODE)
        String marketplaceCode,
        String categoryName
) {
}
