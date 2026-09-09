package com.billbuddy.backend.features.friends.controller;

import com.billbuddy.backend.config.SecurityConfig;
import com.billbuddy.backend.features.auth.security.JwtService;
import com.billbuddy.backend.features.expenses.dto.request.CreateExpenseRequest;
import com.billbuddy.backend.features.expenses.dto.request.PayerEntry;
import com.billbuddy.backend.features.expenses.dto.response.ExpenseResponse;
import com.billbuddy.backend.features.expenses.model.SplitType;
import com.billbuddy.backend.features.expenses.service.ExpenseService;
import com.billbuddy.backend.features.friends.dto.response.FriendBalanceResponse;
import com.billbuddy.backend.features.settlements.service.BalanceService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FriendExpenseController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class FriendExpenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExpenseService expenseService;

    @MockBean
    private BalanceService balanceService;

    @MockBean
    private JwtService jwtService;

    private static final String VALID_TOKEN = "valid-access-token";

    private void stubValidAccessToken(Long userId) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(String.valueOf(userId));
        when(claims.get("email", String.class)).thenReturn("jane@example.com");
        when(jwtService.parseAndValidate(VALID_TOKEN)).thenReturn(claims);
    }

    private ExpenseResponse sampleExpenseResponse() {
        return new ExpenseResponse(
                1L, null, "Coffee", new BigDecimal("20.00"), "INR", new BigDecimal("20.00"), BigDecimal.ONE,
                null, null, SplitType.EQUAL, 1L, "Jane Doe", List.of(), List.of(), List.of(),
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Test
    void createFriendExpense_returns201() throws Exception {
        stubValidAccessToken(1L);
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setDescription("Coffee");
        request.setAmount(new BigDecimal("20"));
        request.setCurrency("INR");
        request.setSplitType(SplitType.EQUAL);
        PayerEntry payer = new PayerEntry();
        payer.setUserId(1L);
        payer.setAmountPaid(new BigDecimal("20"));
        request.setPayers(List.of(payer));
        request.setParticipantUserIds(List.of(1L, 2L));

        when(expenseService.createFriendExpense(eq(1L), eq(2L), any())).thenReturn(sampleExpenseResponse());

        mockMvc.perform(post("/api/v1/friends/2/expenses")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupId").doesNotExist());
    }

    @Test
    void listFriendExpenses_returns200() throws Exception {
        stubValidAccessToken(1L);
        when(expenseService.listFriendExpenses(1L, 2L)).thenReturn(List.of(sampleExpenseResponse()));

        mockMvc.perform(get("/api/v1/friends/2/expenses")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getFriendBalance_returns200() throws Exception {
        stubValidAccessToken(1L);
        when(balanceService.getFriendBalance(1L, 2L))
                .thenReturn(List.of(new FriendBalanceResponse("INR", new BigDecimal("40.00"))));

        mockMvc.perform(get("/api/v1/friends/2/balance")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currency").value("INR"))
                .andExpect(jsonPath("$[0].amount").value(40.00));
    }
}
