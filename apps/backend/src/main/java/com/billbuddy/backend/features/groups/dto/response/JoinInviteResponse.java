package com.billbuddy.backend.features.groups.dto.response;

import com.billbuddy.backend.features.groups.model.GroupRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class JoinInviteResponse {

    private Long groupId;
    private String groupName;
    private GroupRole role;
    private String message;
}
