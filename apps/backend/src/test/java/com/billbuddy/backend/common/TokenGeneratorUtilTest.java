package com.billbuddy.backend.common;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TokenGeneratorUtilTest {

    @Test
    void generate_returnsANonBlankToken() {
        String token = TokenGeneratorUtil.generate();

        assertThat(token).isNotBlank();
    }

    @Test
    void generate_producesUrlSafeCharactersOnly() {
        String token = TokenGeneratorUtil.generate();

        assertThat(token).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    void generate_producesDifferentTokens_acrossManyCalls() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(TokenGeneratorUtil.generate());
        }

        assertThat(tokens).hasSize(1000);
    }
}
