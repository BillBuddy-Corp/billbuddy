package com.billbuddy.backend.features.auth.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHashUtilTest {

    @Test
    void sha256_isDeterministic_forSameInput() {
        String hash1 = TokenHashUtil.sha256("some-refresh-token");
        String hash2 = TokenHashUtil.sha256("some-refresh-token");

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void sha256_producesDifferentHashes_forDifferentInputs() {
        String hash1 = TokenHashUtil.sha256("token-a");
        String hash2 = TokenHashUtil.sha256("token-b");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void sha256_producesLowercase64CharacterHexString() {
        String hash = TokenHashUtil.sha256("any-token-value");

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("^[0-9a-f]{64}$");
    }

    @Test
    void sha256_neverReturnsTheRawInput() {
        String rawToken = "raw-refresh-token-value";

        String hash = TokenHashUtil.sha256(rawToken);

        assertThat(hash).isNotEqualTo(rawToken);
    }
}
