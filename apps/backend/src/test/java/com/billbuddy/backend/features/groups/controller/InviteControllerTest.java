package com.billbuddy.backend.features.groups.controller;

import com.billbuddy.backend.config.SecurityConfig;
import com.billbuddy.backend.exception.InvalidInviteException;
import com.billbuddy.backend.features.auth.security.JwtService;
import com.billbuddy.backend.features.groups.dto.request.JoinInviteRequest;
import com.billbuddy.backend.features.groups.dto.response.JoinInviteResponse;
import com.billbuddy.backend.features.groups.model.GroupRole;
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InviteController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class InviteControllerTest {

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

    @Test
    void join_returns200_whenTokenValid() throws Exception {
        stubValidAccessToken(2L);
        JoinInviteRequest request = new JoinInviteRequest();
        request.setToken("raw-link-token");

        when(groupInviteService.joinViaInvite(2L, "raw-link-token")).thenReturn(
                new JoinInviteResponse(1L, "Goa Trip", GroupRole.MEMBER, "Joined group successfully")
        );

        mockMvc.perform(post("/api/v1/invites/join")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(1))
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    void join_returns400_whenTokenBlank() throws Exception {
        stubValidAccessToken(2L);
        JoinInviteRequest request = new JoinInviteRequest();
        request.setToken("");

        mockMvc.perform(post("/api/v1/invites/join")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void join_returns400_whenInviteInvalid() throws Exception {
        stubValidAccessToken(2L);
        JoinInviteRequest request = new JoinInviteRequest();
        request.setToken("expired-token");

        when(groupInviteService.joinViaInvite(2L, "expired-token"))
                .thenThrow(new InvalidInviteException("This invite has expired"));

        mockMvc.perform(post("/api/v1/invites/join")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
