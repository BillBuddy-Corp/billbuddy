package com.billbuddy.backend.features.groups.controller;

import com.billbuddy.backend.config.SecurityConfig;
import com.billbuddy.backend.features.auth.security.JwtService;
import com.billbuddy.backend.features.groups.dto.request.ChangeMemberRoleRequest;
import com.billbuddy.backend.features.groups.dto.response.GroupMemberResponse;
import com.billbuddy.backend.features.groups.model.GroupRole;
import com.billbuddy.backend.features.groups.service.GroupMemberService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GroupMemberController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class GroupMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GroupMemberService groupMemberService;

    @MockBean
    private JwtService jwtService;

    private static final String VALID_TOKEN = "valid-access-token";

    private void stubValidAccessToken(Long userId) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(String.valueOf(userId));
        when(claims.get("email", String.class)).thenReturn("jane@example.com");
        when(jwtService.parseAndValidate(VALID_TOKEN)).thenReturn(claims);
    }

    private GroupMemberResponse sampleMemberResponse(Long userId, GroupRole role) {
        return new GroupMemberResponse(userId, "Jane Doe", "jane@example.com", role, LocalDateTime.now());
    }

    @Test
    void listMembers_returns200() throws Exception {
        stubValidAccessToken(1L);
        when(groupMemberService.listMembers(1L, 1L))
                .thenReturn(List.of(sampleMemberResponse(1L, GroupRole.ADMIN)));

        mockMvc.perform(get("/api/v1/groups/1/members")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void removeMember_returns204() throws Exception {
        stubValidAccessToken(1L);

        mockMvc.perform(delete("/api/v1/groups/1/members/2")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNoContent());
    }

    @Test
    void changeRole_returns200() throws Exception {
        stubValidAccessToken(1L);
        ChangeMemberRoleRequest request = new ChangeMemberRoleRequest();
        request.setRole(GroupRole.ADMIN);

        when(groupMemberService.changeRole(eq(1L), eq(1L), eq(2L), eq(GroupRole.ADMIN)))
                .thenReturn(sampleMemberResponse(2L, GroupRole.ADMIN));

        mockMvc.perform(put("/api/v1/groups/1/members/2/role")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void changeRole_returns400_whenRoleMissing() throws Exception {
        stubValidAccessToken(1L);

        mockMvc.perform(put("/api/v1/groups/1/members/2/role")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
