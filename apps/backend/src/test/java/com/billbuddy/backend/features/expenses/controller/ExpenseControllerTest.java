package com.billbuddy.backend.features.expenses.controller;

import com.billbuddy.backend.config.SecurityConfig;
import com.billbuddy.backend.features.auth.security.JwtService;
import com.billbuddy.backend.features.expenses.dto.request.CreateExpenseRequest;
import com.billbuddy.backend.features.expenses.dto.request.PayerEntry;
import com.billbuddy.backend.features.expenses.dto.response.ExpenseResponse;
import com.billbuddy.backend.features.expenses.model.SplitType;
import com.billbuddy.backend.features.expenses.service.ExpenseService;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpenseController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ExpenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExpenseService expenseService;

    @MockBean
    private JwtService jwtService;

    private static final String VALID_TOKEN = "valid-access-token";

    private void stubValidAccessToken(Long userId) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(String.valueOf(userId));
        when(claims.get("email", String.class)).thenReturn("jane@example.com");
        when(jwtService.parseAndValidate(VALID_TOKEN)).thenReturn(claims);
    }

    private ExpenseResponse sampleResponse() {
        return new ExpenseResponse(
                1L, 10L, "Dinner", new BigDecimal("90"), "INR", new BigDecimal("90"), BigDecimal.ONE,
                null, null, SplitType.EQUAL, 1L, "Jane Doe",
                List.of(), List.of(), List.of(),
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Test
    void createExpense_returns201_whenValid() throws Exception {
        stubValidAccessToken(1L);
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setDescription("Dinner");
        request.setAmount(new BigDecimal("90"));
        request.setCurrency("INR");
        request.setSplitType(SplitType.EQUAL);
        PayerEntry payer = new PayerEntry();
        payer.setUserId(1L);
        payer.setAmountPaid(new BigDecimal("90"));
        request.setPayers(List.of(payer));
        request.setParticipantUserIds(List.of(1L));

        when(expenseService.createExpense(eq(10L), eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/groups/10/expenses")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Dinner"));
    }

    @Test
    void createExpense_returns400_whenDescriptionBlank() throws Exception {
        stubValidAccessToken(1L);
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setDescription("");
        request.setAmount(new BigDecimal("90"));
        request.setCurrency("INR");
        request.setSplitType(SplitType.EQUAL);
        PayerEntry payer = new PayerEntry();
        payer.setUserId(1L);
        payer.setAmountPaid(new BigDecimal("90"));
        request.setPayers(List.of(payer));

        mockMvc.perform(post("/api/v1/groups/10/expenses")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createExpense_returns400_whenNoPayers() throws Exception {
        stubValidAccessToken(1L);
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setDescription("Dinner");
        request.setAmount(new BigDecimal("90"));
        request.setCurrency("INR");
        request.setSplitType(SplitType.EQUAL);
        request.setPayers(List.of());

        mockMvc.perform(post("/api/v1/groups/10/expenses")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createExpense_returns403_whenNoAuthorizationHeader() throws Exception {
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setDescription("Dinner");
        request.setAmount(new BigDecimal("90"));
        request.setCurrency("INR");
        request.setSplitType(SplitType.EQUAL);
        PayerEntry payer = new PayerEntry();
        payer.setUserId(1L);
        payer.setAmountPaid(new BigDecimal("90"));
        request.setPayers(List.of(payer));

        mockMvc.perform(post("/api/v1/groups/10/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listExpenses_returns200() throws Exception {
        stubValidAccessToken(1L);
        when(expenseService.listExpenses(10L, 1L)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/groups/10/expenses")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
