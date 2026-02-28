package com.mustafabulu.smartpantry.common.controller.impl;

import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.common.dto.request.AuthTokenRequest;
import com.mustafabulu.smartpantry.common.dto.response.AuthTokenResponse;
import com.mustafabulu.smartpantry.common.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final String adminUsername;
    private final String adminPassword;
    private final JwtService jwtService;

    public AuthController(
            @Value("${security.jwt.admin.username:admin}") String adminUsername,
            @Value("${security.jwt.admin.password:}") String adminPassword,
            JwtService jwtService
    ) {
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.jwtService = jwtService;
    }

    @PostMapping("/token")
    public ResponseEntity<AuthTokenResponse> issueToken(@Valid @RequestBody AuthTokenRequest request) {
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new SPException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AUTH_NOT_CONFIGURED",
                    "Admin password is not configured."
            );
        }
        if (!adminUsername.equals(request.username()) || !adminPassword.equals(request.password())) {
            throw new SPException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "Invalid credentials.");
        }
        String token = jwtService.issueToken(request.username(), "ADMIN");
        return ResponseEntity.ok(new AuthTokenResponse(token, "Bearer"));
    }
}
