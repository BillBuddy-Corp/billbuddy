package com.billbuddy.backend.common;

import java.security.SecureRandom;
import java.util.Base64;

public final class TokenGeneratorUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private TokenGeneratorUtil() {
        // prevent instantiation
    }

    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
