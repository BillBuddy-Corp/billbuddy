package com.billbuddy.backend.features.expenses.controller;

import com.billbuddy.backend.config.SecurityConfig;
import com.billbuddy.backend.exception.NotRecurringExpenseOwnerException;
import com.billbuddy.backend.exception.RecurringExpenseNotFoundException;
import com.billbuddy.backend.features.auth.security.JwtService;
import com.billbuddy.backend.features.expenses.service.RecurringExpenseService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecurringExpenseDetailController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class RecurringExpenseDetailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecurringExpenseService recurringExpenseService;

    @MockBean
    private JwtService jwtService;

    private static final String VALID_TOKEN = "valid-access-token";

    private void stubValidAccessToken(Long userId) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(String.valueOf(userId));
        when(claims.get("email", String.class)).thenReturn("jane@example.com");
        when(jwtService.parseAndValidate(VALID_TOKEN)).thenReturn(claims);
    }

    @Test
    void pauseTemplate_returns200() throws Exception {
        stubValidAccessToken(1L);
        doNothing().when(recurringExpenseService).pauseTemplate(1L, 1L);

        mockMvc.perform(post("/api/v1/recurring-expenses/1/pause")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void resumeTemplate_returns200() throws Exception {
        stubValidAccessToken(1L);
        doNothing().when(recurringExpenseService).resumeTemplate(1L, 1L);

        mockMvc.perform(post("/api/v1/recurring-expenses/1/resume")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void pauseTemplate_returns403_whenServiceThrowsNotOwner() throws Exception {
        stubValidAccessToken(2L);
        doThrow(new NotRecurringExpenseOwnerException("Only the person who created this recurring expense or a group admin can modify it"))
                .when(recurringExpenseService).pauseTemplate(1L, 2L);

        mockMvc.perform(post("/api/v1/recurring-expenses/1/pause")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelTemplate_returns204() throws Exception {
        stubValidAccessToken(1L);

        mockMvc.perform(delete("/api/v1/recurring-expenses/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancelTemplate_returns404_whenServiceThrowsNotFound() throws Exception {
        stubValidAccessToken(1L);
        doThrow(new RecurringExpenseNotFoundException("Recurring expense not found"))
                .when(recurringExpenseService).cancelTemplate(1L, 1L);

        mockMvc.perform(delete("/api/v1/recurring-expenses/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void pauseTemplate_returns403_whenNoAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/recurring-expenses/1/pause"))
                .andExpect(status().isForbidden());
    }
}
