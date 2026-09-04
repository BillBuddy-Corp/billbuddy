package com.billbuddy.backend.support;

import com.billbuddy.backend.exception.ReceiptScanFailedException;
import com.billbuddy.backend.features.auth.dto.request.LoginRequest;
import com.billbuddy.backend.features.auth.dto.request.SignupRequest;
import com.billbuddy.backend.features.billscanner.service.ReceiptScannerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillScannerFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReceiptScannerService receiptScannerService;

    private record LoggedInUser(Long userId, String token) {
    }

    private LoggedInUser signupAndLogin(String emailPrefix, String deviceId) throws Exception {
        String email = emailPrefix + "-" + System.nanoTime() + "@example.com";

        SignupRequest signup = new SignupRequest();
        signup.setFullName("Test User");
        signup.setEmail(email);
        signup.setPassword("password123");
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("password123");
        login.setDeviceId(deviceId);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new LoggedInUser(body.get("userId").asLong(), body.get("accessToken").asText());
    }

    private long uploadFile(String token) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", "fake-bytes".getBytes());
        MvcResult result = mockMvc.perform(multipart("/api/v1/files")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private ReceiptScannerService.ScannedReceipt sampleScannedReceipt() {
        return new ReceiptScannerService.ScannedReceipt(
                "Trader Joe's",
                new BigDecimal("45.67"),
                "USD",
                LocalDate.of(2026, 3, 15),
                null,
                null,
                List.of(
                        new ReceiptScannerService.ScannedLineItem("Bananas", new BigDecimal("2.50"), 1, null),
                        new ReceiptScannerService.ScannedLineItem("Bread", new BigDecimal("4.20"), 2, null)
                )
        );
    }

    @Test
    void scanReceipt_returnsExtractedData_andCachesOnSecondCall() throws Exception {
        LoggedInUser jane = signupAndLogin("jane", "device-jane");
        long fileId = uploadFile(jane.token());

        when(receiptScannerService.scan(any(), any())).thenReturn(sampleScannedReceipt());

        mockMvc.perform(post("/api/v1/files/" + fileId + "/scan-receipt")
                        .header("Authorization", "Bearer " + jane.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchant").value("Trader Joe's"))
                .andExpect(jsonPath("$.amount").value(45.67))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.transactionDate").value("2026-03-15"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Bananas"));

        // second call returns the cached result without invoking the scanner again
        mockMvc.perform(post("/api/v1/files/" + fileId + "/scan-receipt")
                        .header("Authorization", "Bearer " + jane.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchant").value("Trader Joe's"));

        verify(receiptScannerService, times(1)).scan(any(), any());
    }

    @Test
    void scanReceipt_returns403_whenRequesterDidNotUploadTheFile() throws Exception {
        LoggedInUser jane = signupAndLogin("jane", "device-jane");
        LoggedInUser bob = signupAndLogin("bob", "device-bob");
        long fileId = uploadFile(jane.token());

        mockMvc.perform(post("/api/v1/files/" + fileId + "/scan-receipt")
                        .header("Authorization", "Bearer " + bob.token()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(receiptScannerService);
    }

    @Test
    void scanReceipt_returns400_whenImageIsNotAReceipt() throws Exception {
        LoggedInUser jane = signupAndLogin("jane", "device-jane");
        long fileId = uploadFile(jane.token());

        when(receiptScannerService.scan(any(), any()))
                .thenThrow(new ReceiptScanFailedException("This doesn't look like a receipt"));

        mockMvc.perform(post("/api/v1/files/" + fileId + "/scan-receipt")
                        .header("Authorization", "Bearer " + jane.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("RECEIPT_SCAN_FAILED"));
    }
}
