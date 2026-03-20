package com.mustafabulu.smartpantry.common.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    @Test
    void issuesAndParsesToken() {
        JwtService jwtService = new JwtService("secret-value-that-is-definitely-long-enough", Duration.ofHours(1));

        String token = jwtService.issueToken("admin", "ADMIN");
        Optional<Claims> claims = jwtService.parse(token);

        assertTrue(claims.isPresent());
        assertEquals("admin", claims.get().getSubject());
        assertEquals("ADMIN", claims.get().get("role", String.class));
    }

    @Test
    void returnsEmptyForInvalidToken() {
        JwtService jwtService = new JwtService("secret-value-that-is-definitely-long-enough", Duration.ofHours(1));

        assertTrue(jwtService.parse("invalid-token").isEmpty());
    }
}
