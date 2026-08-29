package com.billbuddy.backend.features.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefreshTokenRequest {
    @NotBlank
    private String refreshToken;

    @NotBlank
    private String deviceId;
}
