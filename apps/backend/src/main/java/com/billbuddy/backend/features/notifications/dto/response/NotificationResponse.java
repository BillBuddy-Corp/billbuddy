package com.billbuddy.backend.features.notifications.dto.response;

import com.billbuddy.backend.features.notifications.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class NotificationResponse {

    private Long id;
    private NotificationType type;
    private String message;
    private Long groupId;
    private Long expenseId;
    private Long settlementId;
    private boolean read;
    private LocalDateTime createdAt;
}
