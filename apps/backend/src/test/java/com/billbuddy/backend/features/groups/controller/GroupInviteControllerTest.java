package com.billbuddy.backend.features.groups.controller;

import com.billbuddy.backend.config.SecurityConfig;
import com.billbuddy.backend.features.auth.security.JwtService;
import com.billbuddy.backend.features.groups.dto.request.EmailInviteRequest;
import com.billbuddy.backend.features.groups.dto.response.GroupInviteResponse;
import com.billbuddy.backend.features.groups.model.GroupInviteType;
import com.billbuddy.backend.features.groups.service.GroupInviteService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GroupInviteController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class GroupInviteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GroupInviteService groupInviteService;

    @MockBean
    private JwtService jwtService;

    private static final String VALID_TOKEN = "valid-access-token";

    private void stubValidAccessToken(Long userId) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(String.valueOf(userId));
        when(claims.get("email", String.class)).thenReturn("jane@example.com");
        when(jwtService.parseAndValidate(VALID_TOKEN)).thenReturn(claims);
    }

    private GroupInviteResponse sampleLinkInviteResponse() {
        return new GroupInviteResponse(
                1L, GroupInviteType.LINK, null, "raw-link-token",
                null, null, false, "Jane Doe", LocalDateTime.now()
        );
    }

    @Test
    void sendEmailInvite_returns201() throws Exception {
        stubValidAccessToken(1L);
        EmailInviteRequest request = new EmailInviteRequest();
        request.setEmail("friend@example.com");

        mockMvc.perform(post("/api/v1/groups/1/invites/email")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Invite sent"));
    }

    @Test
    void sendEmailInvite_returns400_whenEmailInvalid() throws Exception {
        stubValidAccessToken(1L);
        EmailInviteRequest request = new EmailInviteRequest();
        request.setEmail("not-an-email");

        mockMvc.perform(post("/api/v1/groups/1/invites/email")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listInvites_returns200() throws Exception {
        stubValidAccessToken(1L);
        when(groupInviteService.listInvites(1L, 1L)).thenReturn(List.of(sampleLinkInviteResponse()));

        mockMvc.perform(get("/api/v1/groups/1/invites")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void revokeInvite_returns204() throws Exception {
        stubValidAccessToken(1L);

        mockMvc.perform(delete("/api/v1/groups/1/invites/5")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNoContent());
    }

    @Test
    void generateLink_returns201() throws Exception {
        stubValidAccessToken(1L);
        when(groupInviteService.generateLink(1L, 1L)).thenReturn(sampleLinkInviteResponse());

        mockMvc.perform(post("/api/v1/groups/1/invites/link/generate")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("raw-link-token"));
    }

    @Test
    void disableLink_returns204() throws Exception {
        stubValidAccessToken(1L);

        mockMvc.perform(delete("/api/v1/groups/1/invites/link")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNoContent());
    }
}
