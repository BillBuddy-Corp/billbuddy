package com.billbuddy.backend.support;

import com.billbuddy.backend.features.auth.dto.request.LoginRequest;
import com.billbuddy.backend.features.auth.dto.request.SignupRequest;
import com.billbuddy.backend.features.auth.dto.request.UpdateProfileRequest;
import com.billbuddy.backend.features.expenses.dto.request.CreateExpenseRequest;
import com.billbuddy.backend.features.expenses.dto.request.PayerEntry;
import com.billbuddy.backend.features.expenses.model.SplitType;
import com.billbuddy.backend.features.groups.dto.request.CreateGroupRequest;
import com.billbuddy.backend.features.groups.dto.request.JoinInviteRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StorageFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private long uploadFile(String token, byte[] bytes) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "pic.png", "image/png", bytes);
        MvcResult result = mockMvc.perform(multipart("/api/v1/files")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createGroup(String token) throws Exception {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Goa Trip");
        request.setDefaultCurrency("INR");

        MvcResult result = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void joinGroupViaLink(long groupId, String adminToken, String joinerToken) throws Exception {
        MvcResult linkResult = mockMvc.perform(post("/api/v1/groups/" + groupId + "/invites/link/generate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(linkResult.getResponse().getContentAsString()).get("token").asText();

        JoinInviteRequest joinRequest = new JoinInviteRequest();
        joinRequest.setToken(token);
        mockMvc.perform(post("/api/v1/invites/join")
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(joinRequest)))
                .andExpect(status().isOk());
    }

    @Test
    void uploadThenAttachAsProfilePicture_visibleToAnyAuthenticatedUser_ownershipEnforced() throws Exception {
        LoggedInUser jane = signupAndLogin("jane", "device-jane");
        LoggedInUser bob = signupAndLogin("bob", "device-bob");

        long fileId = uploadFile(jane.token(), "fake-png-bytes".getBytes());

        // orphaned file: uploader can view it immediately
        mockMvc.perform(get("/api/v1/files/" + fileId)
                        .header("Authorization", "Bearer " + jane.token()))
                .andExpect(status().isOk());

        // orphaned file: nobody else can view it yet
        mockMvc.perform(get("/api/v1/files/" + fileId)
                        .header("Authorization", "Bearer " + bob.token()))
                .andExpect(status().isForbidden());

        // Bob can't attach a file Jane uploaded
        UpdateProfileRequest bobAttempt = new UpdateProfileRequest();
        bobAttempt.setFullName("Bob Smith");
        bobAttempt.setProfilePicFileId(fileId);
        bobAttempt.setDefaultCurrency("INR");
        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + bob.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bobAttempt)))
                .andExpect(status().isForbidden());

        // Jane attaches her own upload as her profile picture
        UpdateProfileRequest janeUpdate = new UpdateProfileRequest();
        janeUpdate.setFullName("Jane Doe");
        janeUpdate.setProfilePicFileId(fileId);
        janeUpdate.setDefaultCurrency("INR");
        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + jane.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(janeUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePicUrl").value("/api/v1/files/" + fileId));

        // now that it's a profile picture, any authenticated user (Bob) can view it
        mockMvc.perform(get("/api/v1/files/" + fileId)
                        .header("Authorization", "Bearer " + bob.token()))
                .andExpect(status().isOk());
    }

    @Test
    void uploadThenAttachAsReceipt_onlyGroupMembersCanView() throws Exception {
        LoggedInUser admin = signupAndLogin("admin", "device-admin");
        LoggedInUser member = signupAndLogin("member", "device-member");
        LoggedInUser outsider = signupAndLogin("outsider", "device-outsider");

        long groupId = createGroup(admin.token());
        joinGroupViaLink(groupId, admin.token(), member.token());

        long fileId = uploadFile(admin.token(), "fake-receipt-bytes".getBytes());

        CreateExpenseRequest expenseRequest = new CreateExpenseRequest();
        expenseRequest.setDescription("Dinner");
        expenseRequest.setAmount(new BigDecimal("90"));
        expenseRequest.setCurrency("INR");
        expenseRequest.setSplitType(SplitType.EQUAL);
        expenseRequest.setReceiptFileId(fileId);
        PayerEntry payer = new PayerEntry();
        payer.setUserId(admin.userId());
        payer.setAmountPaid(new BigDecimal("90"));
        expenseRequest.setPayers(List.of(payer));
        expenseRequest.setParticipantUserIds(List.of(admin.userId(), member.userId()));

        mockMvc.perform(post("/api/v1/groups/" + groupId + "/expenses")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expenseRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptUrl").value("/api/v1/files/" + fileId));

        // group member can view the receipt
        mockMvc.perform(get("/api/v1/files/" + fileId)
                        .header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isOk());

        // non-member cannot
        mockMvc.perform(get("/api/v1/files/" + fileId)
                        .header("Authorization", "Bearer " + outsider.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void upload_rejectsOversizedOrWrongContentType() throws Exception {
        LoggedInUser jane = signupAndLogin("jane", "device-jane");

        MockMultipartFile wrongType = new MockMultipartFile("file", "doc.pdf", "application/pdf", "not-an-image".getBytes());
        mockMvc.perform(multipart("/api/v1/files")
                        .file(wrongType)
                        .header("Authorization", "Bearer " + jane.token()))
                .andExpect(status().isBadRequest());

        byte[] tooLarge = new byte[6 * 1024 * 1024];
        MockMultipartFile oversized = new MockMultipartFile("file", "big.png", "image/png", tooLarge);
        mockMvc.perform(multipart("/api/v1/files")
                        .file(oversized)
                        .header("Authorization", "Bearer " + jane.token()))
                .andExpect(status().isBadRequest());
    }
}
