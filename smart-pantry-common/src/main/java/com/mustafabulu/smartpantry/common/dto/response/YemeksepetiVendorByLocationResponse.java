package com.mustafabulu.smartpantry.common.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resolved Yemeksepeti vendor information from coordinates")
public record YemeksepetiVendorByLocationResponse(
        @Schema(
                description = "Vendor redirection URL",
                example = "https://yemeksepeti.com/restaurant/vj0c/yemeksepeti-market-karliktepe-istanbul"
        )
        String redirectionUrl
) {
}
