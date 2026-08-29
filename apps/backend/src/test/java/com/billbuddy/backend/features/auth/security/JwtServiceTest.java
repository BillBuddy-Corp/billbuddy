package com.billbuddy.backend.features.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET =
            "unit-test-jwt-secret-key-for-testing-purposes-only-1234567890";

    private final JwtService jwtService = new JwtService(SECRET, 15, 30);

    @Test
    void generateAccessToken_containsUserIdAndEmailClaims() {
        String token = jwtService.generateAccessToken(42L, "jane@example.com");

        Claims claims = jwtService.parseAndValidate(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("email", String.class)).isEqualTo("jane@example.com");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void generateRefreshToken_containsUserIdButNoEmailClaim() {
        String token = jwtService.generateRefreshToken(42L);

        Claims claims = jwtService.parseAndValidate(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("email")).isNull();
    }

    @Test
    void parseAndValidate_throwsExpiredJwtException_forExpiredToken() {
        JwtService expiredIssuer = new JwtService(SECRET, -1, -1);
        String expiredToken = expiredIssuer.generateAccessToken(1L, "jane@example.com");

        assertThatThrownBy(() -> jwtService.parseAndValidate(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parseAndValidate_throwsSignatureException_whenSignedWithDifferentSecret() {
        JwtService otherIssuer = new JwtService(
                "a-completely-different-secret-key-used-to-sign-1234567890", 15, 30
        );
        String tokenFromOtherIssuer = otherIssuer.generateAccessToken(1L, "jane@example.com");

        assertThatThrownBy(() -> jwtService.parseAndValidate(tokenFromOtherIssuer))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void hashToken_isDeterministic() {
        String hash1 = jwtService.hashToken("raw-refresh-token");
        String hash2 = jwtService.hashToken("raw-refresh-token");

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isNotEqualTo("raw-refresh-token");
    }

    @Test
    void generateRefreshToken_isUnique_evenWhenCalledTwiceInTheSameInstant() {
        // Regression test: sub/iat/exp alone can be identical for two calls issued
        // in the same second, which would make the signed JWT byte-for-byte identical
        // and collide on the refresh_tokens.token_hash unique constraint. The random
        // jti claim guarantees distinct tokens regardless of timing.
        String token1 = jwtService.generateRefreshToken(1L);
        String token2 = jwtService.generateRefreshToken(1L);

        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    void generateAccessToken_isUnique_evenWhenCalledTwiceInTheSameInstant() {
        String token1 = jwtService.generateAccessToken(1L, "jane@example.com");
        String token2 = jwtService.generateAccessToken(1L, "jane@example.com");

        assertThat(token1).isNotEqualTo(token2);
    }
}
