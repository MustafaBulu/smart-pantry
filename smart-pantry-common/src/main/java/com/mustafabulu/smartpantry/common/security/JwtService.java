package com.mustafabulu.smartpantry.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final Duration ttl;

    public JwtService(
            @Value("${security.jwt.secret:}") String jwtSecret,
            @Value("${security.jwt.ttl:PT8H}") Duration ttl
    ) {
        this.secretKey = Keys.hmacShaKeyFor(padSecret(jwtSecret).getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
    }

    public String issueToken(String subject, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(secretKey)
                .compact();
    }

    public Optional<Claims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static String padSecret(String input) {
        String value = input == null ? "" : input.trim();
        if (value.length() >= 32) {
            return value;
        }
        return (value + "smart-pantry-default-jwt-secret-rotate-me-2026").substring(0, 32);
    }
}
