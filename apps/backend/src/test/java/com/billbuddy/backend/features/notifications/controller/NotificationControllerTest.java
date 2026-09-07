package com.billbuddy.backend.features.notifications.controller;

import com.billbuddy.backend.config.SecurityConfig;
import com.billbuddy.backend.exception.NotificationNotFoundException;
import com.billbuddy.backend.features.auth.security.JwtService;
import com.billbuddy.backend.features.notifications.dto.response.NotificationResponse;
import com.billbuddy.backend.features.notifications.model.NotificationType;
import com.billbuddy.backend.features.notifications.service.NotificationService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private JwtService jwtService;

    private static final String VALID_TOKEN = "valid-access-token";

    private void stubValidAccessToken(Long userId) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(String.valueOf(userId));
        when(claims.get("email", String.class)).thenReturn("jane@example.com");
        when(jwtService.parseAndValidate(VALID_TOKEN)).thenReturn(claims);
    }

    private NotificationResponse sampleResponse() {
        return new NotificationResponse(1L, NotificationType.EXPENSE_CREATED, "Jane added an expense", 10L, 100L, null, false, LocalDateTime.now());
    }

    @Test
    void listNotifications_returns200() throws Exception {
        stubValidAccessToken(1L);
        when(notificationService.listNotifications(1L)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].message").value("Jane added an expense"));
    }

    @Test
    void listNotifications_returns403_whenNoAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unreadCount_returns200() throws Exception {
        stubValidAccessToken(1L);
        when(notificationService.unreadCount(1L)).thenReturn(3L);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3));
    }

    @Test
    void markAsRead_returns200() throws Exception {
        stubValidAccessToken(1L);

        mockMvc.perform(post("/api/v1/notifications/1/read")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void markAsRead_returns404_whenNotFound() throws Exception {
        stubValidAccessToken(1L);
        doThrow(new NotificationNotFoundException("Notification not found"))
                .when(notificationService).markAsRead(1L, 1L);

        mockMvc.perform(post("/api/v1/notifications/1/read")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void markAllAsRead_returns200() throws Exception {
        stubValidAccessToken(1L);

        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk());
    }
}
