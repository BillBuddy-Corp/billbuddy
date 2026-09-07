package com.billbuddy.backend.features.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GoogleSignInRequest {

    @NotBlank
    private String idToken;

    @NotBlank
    private String deviceId;

    private String deviceName;

}
