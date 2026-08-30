package com.billbuddy.backend.features.groups.dto.response;

import com.billbuddy.backend.features.groups.model.GroupInviteType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class GroupInviteResponse {

    private Long id;
    private GroupInviteType type;
    private String email;

    // only ever populated for LINK invites — EMAIL tokens are hashed and never re-exposed
    private String token;

    private LocalDateTime expiresAt;
    private LocalDateTime acceptedAt;
    private boolean revoked;
    private String invitedByName;
    private LocalDateTime createdAt;
}
