package com.billbuddy.backend.features.groups.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class MyInviteResponse {

    private Long id;
    private Long groupId;
    private String groupName;
    private String invitedByName;
    private LocalDateTime createdAt;
}
