package com.billbuddy.backend.features.notifications.controller;

import com.billbuddy.backend.features.notifications.dto.response.NotificationResponse;
import com.billbuddy.backend.features.notifications.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Notification feed, unread count, and mark-as-read APIs")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "List notifications", description = "Lists the authenticated user's notifications, newest first")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Notifications retrieved successfully")
    })
    public ResponseEntity<List<NotificationResponse>> listNotifications(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(notificationService.listNotifications(userId));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread count", description = "Returns the number of unread notifications for the authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Unread count retrieved successfully")
    })
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(Map.of("unreadCount", notificationService.unreadCount(userId)));
    }

    @PostMapping("/{notificationId}/read")
    @Operation(summary = "Mark one notification as read", description = "Only affects notifications belonging to the authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Notification marked as read successfully"),
            @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long notificationId
    ) {
        notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all notifications as read", description = "Marks every unread notification belonging to the authenticated user as read")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Notifications marked as read successfully")
    })
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal Long userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }
}
