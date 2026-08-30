package com.billbuddy.backend.support;

import com.billbuddy.backend.features.auth.dto.request.LoginRequest;
import com.billbuddy.backend.features.auth.dto.request.SignupRequest;
import com.billbuddy.backend.features.groups.dto.request.ChangeMemberRoleRequest;
import com.billbuddy.backend.features.groups.dto.request.CreateGroupRequest;
import com.billbuddy.backend.features.groups.dto.request.JoinInviteRequest;
import com.billbuddy.backend.features.groups.model.GroupRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// EMAIL invite flow isn't exercised here — it needs a reachable SMTP server.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GroupsFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String signupAndLogin(String emailPrefix, String deviceId) throws Exception {
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

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Test
    void groupLifecycle_createInviteJoinRoleChangeAndLastAdminGuard() throws Exception {
        String adminToken = signupAndLogin("admin", "device-admin");
        String memberToken = signupAndLogin("member", "device-member");

        // ---- create group: creator becomes Admin ----
        CreateGroupRequest createRequest = new CreateGroupRequest();
        createRequest.setName("Goa Trip");
        createRequest.setDescription("Beach house squad");
        createRequest.setDefaultCurrency("INR");

        MvcResult createResult = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentUserRole").value("ADMIN"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andReturn();

        JsonNode createdGroup = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long groupId = createdGroup.get("id").asLong();
        long adminUserId = createdGroup.get("createdByUserId").asLong();

        // ---- generate a shareable link (LINK invites need no SMTP) ----
        MvcResult linkResult = mockMvc.perform(post("/api/v1/groups/" + groupId + "/invites/link/generate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("LINK"))
                .andReturn();

        String linkToken = objectMapper.readTree(linkResult.getResponse().getContentAsString())
                .get("token").asText();
        assertThat(linkToken).isNotBlank();

        // ---- second user joins via the link ----
        JoinInviteRequest joinRequest = new JoinInviteRequest();
        joinRequest.setToken(linkToken);

        mockMvc.perform(post("/api/v1/invites/join")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(joinRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));

        // ---- member list now shows both, admin listing works ----
        mockMvc.perform(get("/api/v1/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // ---- non-admin can't hit an admin-only action ----
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/invites/link/generate")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());

        // ---- get the member's userId for role/removal calls ----
        MvcResult membersResult = mockMvc.perform(get("/api/v1/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn();
        JsonNode members = objectMapper.readTree(membersResult.getResponse().getContentAsString());
        long memberUserId = -1;
        for (JsonNode m : members) {
            if (m.get("role").asText().equals("MEMBER")) {
                memberUserId = m.get("userId").asLong();
            }
        }
        assertThat(memberUserId).isPositive();

        // ---- promote the member to admin ----
        ChangeMemberRoleRequest promote = new ChangeMemberRoleRequest();
        promote.setRole(GroupRole.ADMIN);

        mockMvc.perform(put("/api/v1/groups/" + groupId + "/members/" + memberUserId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(promote)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // ---- demote the ORIGINAL creator back to member, now testing the last-admin guard ----
        // first demote memberUserId back down so only the original creator is admin again
        ChangeMemberRoleRequest demoteBack = new ChangeMemberRoleRequest();
        demoteBack.setRole(GroupRole.MEMBER);
        mockMvc.perform(put("/api/v1/groups/" + groupId + "/members/" + memberUserId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(demoteBack)))
                .andExpect(status().isOk());

        // sole admin trying to leave while another active member remains -> blocked (409)
        mockMvc.perform(delete("/api/v1/groups/" + groupId + "/members/" + adminUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        // ---- remove the member outright (admin removing someone else never trips the guard) ----
        mockMvc.perform(delete("/api/v1/groups/" + groupId + "/members/" + memberUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
