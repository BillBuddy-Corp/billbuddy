package com.billbuddy.backend.features.groups.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateGroupRequest {

    @NotBlank(message = "Group name is required")
    private String name;

    private String description;

    @NotBlank(message = "Default currency is required")
    private String defaultCurrency;
}
