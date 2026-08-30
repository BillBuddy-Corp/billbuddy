package com.billbuddy.backend.features.groups.dto.request;

import com.billbuddy.backend.features.groups.model.GroupRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChangeMemberRoleRequest {

    @NotNull(message = "Role is required")
    private GroupRole role;
}
