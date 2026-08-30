package com.billbuddy.backend.features.groups.dto.response;

import com.billbuddy.backend.features.groups.model.GroupRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class GroupMemberResponse {

    private Long userId;
    private String fullName;
    private String email;
    private GroupRole role;
    private LocalDateTime joinedAt;
}
