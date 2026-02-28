package com.mustafabulu.smartpantry.common.core.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorData {

    @Schema(description = "Timestamp when the error occurred", example = "2025-12-24T10:15:30.123")
    private String timestamp;

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "Short error category or HTTP reason", example = "Validation Error")
    private String error;

    @Schema(description = "Detailed error message", example = "Request data is invalid.")
    private String message;

    @Schema(description = "Request path", example = "/api/categories")
    private String path;
}
