package com.mustafabulu.smartpantry.common.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AuthTokenRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
