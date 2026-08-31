package com.billbuddy.backend.features.settlements.controller;

import com.billbuddy.backend.config.SecurityConfig;
import com.billbuddy.backend.features.auth.security.JwtService;
import com.billbuddy.backend.features.settlements.dto.request.CreateSettlementRequest;
import com.billbuddy.backend.features.settlements.dto.response.SettlementResponse;
import com.billbuddy.backend.features.settlements.service.SettlementService;
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

@WebMvcTest(SettlementController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class SettlementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SettlementService settlementService;

    @MockBean
    private JwtService jwtService;

    private static final String VALID_TOKEN = "valid-access-token";

    private void stubValidAccessToken(Long userId) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(String.valueOf(userId));
        when(claims.get("email", String.class)).thenReturn("jane@example.com");
        when(jwtService.parseAndValidate(VALID_TOKEN)).thenReturn(claims);
    }

    private SettlementResponse sampleResponse() {
        return new SettlementResponse(
                1L, 10L, 2L, "Bob Smith", 1L, "Jane Doe",
                new BigDecimal("50"), "INR", "Cash at dinner",
                2L, "Bob Smith", LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Test
    void createSettlement_returns201_whenValid() throws Exception {
        stubValidAccessToken(2L);
        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setPaidByUserId(2L);
        request.setPaidToUserId(1L);
        request.setAmount(new BigDecimal("50"));
        request.setCurrency("INR");

        when(settlementService.createSettlement(eq(10L), eq(2L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/groups/10/settlements")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paidByUserId").value(2))
                .andExpect(jsonPath("$.paidToUserId").value(1));
    }

    @Test
    void createSettlement_returns400_whenPaidByUserIdMissing() throws Exception {
        stubValidAccessToken(2L);
        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setPaidToUserId(1L);
        request.setAmount(new BigDecimal("50"));
        request.setCurrency("INR");

        mockMvc.perform(post("/api/v1/groups/10/settlements")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSettlement_returns400_whenAmountNotPositive() throws Exception {
        stubValidAccessToken(2L);
        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setPaidByUserId(2L);
        request.setPaidToUserId(1L);
        request.setAmount(new BigDecimal("0"));
        request.setCurrency("INR");

        mockMvc.perform(post("/api/v1/groups/10/settlements")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSettlement_returns403_whenNoAuthorizationHeader() throws Exception {
        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setPaidByUserId(2L);
        request.setPaidToUserId(1L);
        request.setAmount(new BigDecimal("50"));
        request.setCurrency("INR");

        mockMvc.perform(post("/api/v1/groups/10/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listSettlements_returns200() throws Exception {
        stubValidAccessToken(2L);
        when(settlementService.listSettlements(10L, 2L)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/groups/10/settlements")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
