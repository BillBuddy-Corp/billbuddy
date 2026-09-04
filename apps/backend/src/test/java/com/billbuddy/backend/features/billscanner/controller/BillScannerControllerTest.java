package com.billbuddy.backend.features.billscanner.controller;

import com.billbuddy.backend.config.SecurityConfig;
import com.billbuddy.backend.exception.NotFileOwnerException;
import com.billbuddy.backend.exception.ReceiptScanFailedException;
import com.billbuddy.backend.exception.StoredFileNotFoundException;
import com.billbuddy.backend.features.auth.security.JwtService;
import com.billbuddy.backend.features.billscanner.dto.response.ReceiptScanItemResponse;
import com.billbuddy.backend.features.billscanner.dto.response.ReceiptScanResponse;
import com.billbuddy.backend.features.billscanner.service.BillScannerService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BillScannerController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class BillScannerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BillScannerService billScannerService;

    @MockBean
    private JwtService jwtService;

    private static final String VALID_TOKEN = "valid-access-token";

    private void stubValidAccessToken(Long userId) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(String.valueOf(userId));
        when(claims.get("email", String.class)).thenReturn("jane@example.com");
        when(jwtService.parseAndValidate(VALID_TOKEN)).thenReturn(claims);
    }

    private ReceiptScanResponse sampleResponse() {
        return new ReceiptScanResponse(
                100L, "Tesco Ireland", new BigDecimal("37.67"), "EUR", null,
                new BigDecimal("7.01"), null, new BigDecimal("41.85"), false, true,
                List.of(new ReceiptScanItemResponse("Bread", new BigDecimal("2.10"), 1))
        );
    }

    @Test
    void scanReceipt_returns200_whenValid() throws Exception {
        stubValidAccessToken(1L);
        when(billScannerService.scanReceipt(eq(100L), eq(1L))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/files/100/scan-receipt")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchant").value("Tesco Ireland"))
                .andExpect(jsonPath("$.amount").value(37.67))
                .andExpect(jsonPath("$.needsReview").value(false))
                .andExpect(jsonPath("$.discountsNeedReview").value(true))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void scanReceipt_returns403_whenNoAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/files/100/scan-receipt"))
                .andExpect(status().isForbidden());
    }

    @Test
    void scanReceipt_returns403_whenRequesterDidNotUploadTheFile() throws Exception {
        stubValidAccessToken(2L);
        when(billScannerService.scanReceipt(eq(100L), eq(2L)))
                .thenThrow(new NotFileOwnerException("You can only attach files you uploaded yourself"));

        mockMvc.perform(post("/api/v1/files/100/scan-receipt")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void scanReceipt_returns404_whenFileNotFound() throws Exception {
        stubValidAccessToken(1L);
        when(billScannerService.scanReceipt(eq(999L), eq(1L)))
                .thenThrow(new StoredFileNotFoundException("File not found"));

        mockMvc.perform(post("/api/v1/files/999/scan-receipt")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void scanReceipt_returns400_whenImageIsNotAReceipt() throws Exception {
        stubValidAccessToken(1L);
        when(billScannerService.scanReceipt(eq(100L), eq(1L)))
                .thenThrow(new ReceiptScanFailedException("This doesn't look like a receipt"));

        mockMvc.perform(post("/api/v1/files/100/scan-receipt")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("RECEIPT_SCAN_FAILED"));
    }
}
