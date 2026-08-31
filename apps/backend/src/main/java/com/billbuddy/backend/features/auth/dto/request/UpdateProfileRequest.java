package com.billbuddy.backend.features.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateProfileRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    private Long profilePicFileId;

    @NotBlank(message = "Default currency is required")
    private String defaultCurrency;
}
