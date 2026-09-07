package com.billbuddy.backend.features.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChangeEmailRequest {

    @NotBlank(message = "New email is required")
    @Email(message = "New email must be a valid email address")
    private String newEmail;

    @NotBlank(message = "Current password is required")
    private String currentPassword;
}
