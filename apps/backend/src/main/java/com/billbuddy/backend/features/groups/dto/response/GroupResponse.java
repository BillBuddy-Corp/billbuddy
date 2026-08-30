package com.billbuddy.backend.features.groups.dto.response;

import com.billbuddy.backend.features.groups.model.GroupRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class GroupResponse {

    private Long id;
    private String name;
    private String description;
    private String defaultCurrency;
    private Long createdByUserId;
    private String createdByName;
    private long memberCount;
    private GroupRole currentUserRole;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
