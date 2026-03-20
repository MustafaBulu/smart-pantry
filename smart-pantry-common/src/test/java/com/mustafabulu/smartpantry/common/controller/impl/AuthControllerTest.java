package com.mustafabulu.smartpantry.common.controller.impl;

import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.common.dto.request.AuthTokenRequest;
import com.mustafabulu.smartpantry.common.dto.response.AuthTokenResponse;
import com.mustafabulu.smartpantry.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private JwtService jwtService;

    @Test
    void throwsWhenAuthIsNotConfigured() {
        AuthController controller = new AuthController("admin", "", jwtService);

        SPException exception = assertThrows(
                SPException.class,
                () -> controller.issueToken(new AuthTokenRequest("admin", "secret"))
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        assertEquals("AUTH_NOT_CONFIGURED", exception.getReason());
    }

    @Test
    void throwsWhenCredentialsAreInvalid() {
        AuthController controller = new AuthController("admin", "secret", jwtService);

        SPException exception = assertThrows(
                SPException.class,
                () -> controller.issueToken(new AuthTokenRequest("admin", "wrong"))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("AUTH_INVALID_CREDENTIALS", exception.getReason());
    }

    @Test
    void returnsBearerTokenWhenCredentialsAreValid() {
        AuthController controller = new AuthController("admin", "secret", jwtService);
        when(jwtService.issueToken("admin", "ADMIN")).thenReturn("jwt-token");

        ResponseEntity<AuthTokenResponse> response = controller.issueToken(new AuthTokenRequest("admin", "secret"));

        assertEquals("jwt-token", response.getBody().token());
        assertEquals("Bearer", response.getBody().tokenType());
    }
}
