package com.billbuddy.backend.features.groups.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class JoinInviteRequest {

    @NotBlank(message = "Token is required")
    private String token;
}
