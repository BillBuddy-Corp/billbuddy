package com.billbuddy.backend.features.settlements.controller;

import com.billbuddy.backend.config.SecurityConfig;
import com.billbuddy.backend.exception.NotSettlementOwnerException;
import com.billbuddy.backend.features.auth.security.JwtService;
import com.billbuddy.backend.features.settlements.dto.request.UpdateSettlementRequest;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettlementDetailController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class SettlementDetailControllerTest {

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

    private UpdateSettlementRequest buildUpdateRequest() {
        UpdateSettlementRequest request = new UpdateSettlementRequest();
        request.setPaidByUserId(2L);
        request.setPaidToUserId(1L);
        request.setAmount(new BigDecimal("25"));
        request.setCurrency("INR");
        return request;
    }

    @Test
    void getSettlement_returns200() throws Exception {
        stubValidAccessToken(1L);
        when(settlementService.getSettlement(1L, 1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/settlements/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void updateSettlement_returns200() throws Exception {
        stubValidAccessToken(2L);
        UpdateSettlementRequest request = buildUpdateRequest();

        when(settlementService.updateSettlement(eq(1L), eq(2L), any())).thenReturn(sampleResponse());

        mockMvc.perform(put("/api/v1/settlements/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void updateSettlement_returns403_whenServiceThrowsNotSettlementOwner() throws Exception {
        stubValidAccessToken(3L);
        UpdateSettlementRequest request = buildUpdateRequest();

        when(settlementService.updateSettlement(eq(1L), eq(3L), any()))
                .thenThrow(new NotSettlementOwnerException("Only the person who logged this settlement or a group admin can modify it"));

        mockMvc.perform(put("/api/v1/settlements/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteSettlement_returns204() throws Exception {
        stubValidAccessToken(2L);

        mockMvc.perform(delete("/api/v1/settlements/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNoContent());
    }
}
