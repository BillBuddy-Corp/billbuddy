package com.billbuddy.backend.features.settlements.controller;

import com.billbuddy.backend.config.SecurityConfig;
import com.billbuddy.backend.features.auth.security.JwtService;
import com.billbuddy.backend.features.settlements.dto.response.BalanceResponse;
import com.billbuddy.backend.features.settlements.dto.response.SimplifiedSettlementResponse;
import com.billbuddy.backend.features.settlements.service.BalanceService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BalanceController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class BalanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

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

    @Test
    void getBalances_returns200() throws Exception {
        stubValidAccessToken(1L);
        when(balanceService.getBalances(10L, 1L)).thenReturn(List.of(
                new BalanceResponse(1L, "Jane Doe", new BigDecimal("100.00")),
                new BalanceResponse(2L, "Bob Smith", new BigDecimal("-100.00"))
        ));

        mockMvc.perform(get("/api/v1/groups/10/balances")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].netBalance").value(100.0));
    }

    @Test
    void getBalances_returns403_whenNoAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/groups/10/balances"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSimplifiedBalances_returns200() throws Exception {
        stubValidAccessToken(1L);
        when(balanceService.getSimplifiedBalances(10L, 1L)).thenReturn(List.of(
                new SimplifiedSettlementResponse(2L, "Bob Smith", 1L, "Jane Doe", new BigDecimal("100.00"))
        ));

        mockMvc.perform(get("/api/v1/groups/10/balances/simplified")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fromUserId").value(2))
                .andExpect(jsonPath("$[0].toUserId").value(1));
    }
}
