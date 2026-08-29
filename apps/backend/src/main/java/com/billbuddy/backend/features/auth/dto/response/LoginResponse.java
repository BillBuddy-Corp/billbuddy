package com.billbuddy.backend.features.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {

    private String tokenType;     // "Bearer"
    private String accessToken;
    private String refreshToken;

    private Long userId;
    private String email;
    private String fullName;
}
