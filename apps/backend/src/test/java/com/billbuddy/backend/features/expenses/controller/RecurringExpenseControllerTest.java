package com.billbuddy.backend.features.expenses.controller;

import com.billbuddy.backend.config.SecurityConfig;
import com.billbuddy.backend.features.auth.security.JwtService;
import com.billbuddy.backend.features.expenses.dto.request.CreateRecurringExpenseRequest;
import com.billbuddy.backend.features.expenses.dto.request.PayerEntry;
import com.billbuddy.backend.features.expenses.dto.response.RecurringExpenseResponse;
import com.billbuddy.backend.features.expenses.model.RecurringFrequency;
import com.billbuddy.backend.features.expenses.model.SplitType;
import com.billbuddy.backend.features.expenses.service.RecurringExpenseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecurringExpenseController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class RecurringExpenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private RecurringExpenseResponse sampleResponse() {
        return new RecurringExpenseResponse(
                1L, 10L, "Rent", new BigDecimal("90"), "INR", null, null, SplitType.EQUAL,
                List.of(), List.of(1L), List.of(), List.of(), List.of(),
                RecurringFrequency.MONTHLY, null, 1, LocalDate.now().plusDays(1), true,
                1L, "Jane Doe", LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private CreateRecurringExpenseRequest buildRequest() {
        CreateRecurringExpenseRequest request = new CreateRecurringExpenseRequest();
        request.setDescription("Rent");
        request.setAmount(new BigDecimal("90"));
        request.setCurrency("INR");
        request.setSplitType(SplitType.EQUAL);
        PayerEntry payer = new PayerEntry();
        payer.setUserId(1L);
        payer.setAmountPaid(new BigDecimal("90"));
        request.setPayers(List.of(payer));
        request.setParticipantUserIds(List.of(1L));
        request.setFrequency(RecurringFrequency.MONTHLY);
        request.setDayOfMonth(1);
        return request;
    }

    @Test
    void createTemplate_returns201_whenValid() throws Exception {
        stubValidAccessToken(1L);
        when(recurringExpenseService.createTemplate(eq(10L), eq(1L), any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/groups/10/recurring-expenses")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Rent"));
    }

    @Test
    void createTemplate_returns400_whenFrequencyMissing() throws Exception {
        stubValidAccessToken(1L);
        CreateRecurringExpenseRequest request = buildRequest();
        request.setFrequency(null);

        mockMvc.perform(post("/api/v1/groups/10/recurring-expenses")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTemplate_returns403_whenNoAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/groups/10/recurring-expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTemplates_returns200() throws Exception {
        stubValidAccessToken(1L);
        when(recurringExpenseService.listTemplates(10L, 1L)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/groups/10/recurring-expenses")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
