package com.billbuddy.backend.features.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    @Pattern(
            regexp = "^\\+[1-9][0-9]{7,14}$",
            message = "Mobile number must be in international format (e.g. +919876543210)"
    )
    private String mobileNumber;
}
