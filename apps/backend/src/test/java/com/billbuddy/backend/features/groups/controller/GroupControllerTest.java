package com.billbuddy.backend.features.groups.controller;

import com.billbuddy.backend.config.SecurityConfig;
import com.billbuddy.backend.exception.NotGroupAdminException;
import com.billbuddy.backend.exception.UnsettledBalancesException;
import com.billbuddy.backend.features.auth.security.JwtService;
import com.billbuddy.backend.features.groups.dto.request.CreateGroupRequest;
import com.billbuddy.backend.features.groups.dto.request.UpdateGroupRequest;
import com.billbuddy.backend.features.groups.dto.response.GroupResponse;
import com.billbuddy.backend.features.groups.model.GroupRole;
import com.billbuddy.backend.features.groups.service.GroupService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GroupController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GroupService groupService;

    @MockBean
    private JwtService jwtService;

    private static final String VALID_TOKEN = "valid-access-token";

    private void stubValidAccessToken(Long userId) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(String.valueOf(userId));
        when(claims.get("email", String.class)).thenReturn("jane@example.com");
        when(jwtService.parseAndValidate(VALID_TOKEN)).thenReturn(claims);
    }

    private GroupResponse sampleGroupResponse() {
        return new GroupResponse(
                1L, "Goa Trip", "Beach house squad", "INR",
                1L, "Jane Doe", 1, GroupRole.ADMIN,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Test
    void createGroup_returns201_whenRequestValid() throws Exception {
        stubValidAccessToken(1L);
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Goa Trip");
        request.setDescription("Beach house squad");
        request.setDefaultCurrency("INR");

        when(groupService.createGroup(eq(1L), any())).thenReturn(sampleGroupResponse());

        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentUserRole").value("ADMIN"));
    }

    @Test
    void createGroup_returns400_whenNameBlank() throws Exception {
        stubValidAccessToken(1L);
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("");
        request.setDefaultCurrency("INR");

        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createGroup_returns403_whenNoAuthorizationHeader() throws Exception {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Goa Trip");
        request.setDefaultCurrency("INR");

        mockMvc.perform(post("/api/v1/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listGroups_returns200() throws Exception {
        stubValidAccessToken(1L);
        when(groupService.listGroups(1L)).thenReturn(List.of(sampleGroupResponse()));

        mockMvc.perform(get("/api/v1/groups")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getGroup_returns200() throws Exception {
        stubValidAccessToken(1L);
        when(groupService.getGroup(1L, 1L)).thenReturn(sampleGroupResponse());

        mockMvc.perform(get("/api/v1/groups/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Goa Trip"));
    }

    @Test
    void updateGroup_returns200() throws Exception {
        stubValidAccessToken(1L);
        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setName("Renamed Trip");
        request.setDescription("Updated");
        request.setDefaultCurrency("USD");

        when(groupService.updateGroup(eq(1L), eq(1L), any())).thenReturn(sampleGroupResponse());

        mockMvc.perform(put("/api/v1/groups/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void updateGroup_returns403_whenServiceThrowsNotGroupAdmin() throws Exception {
        stubValidAccessToken(1L);
        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setName("Renamed Trip");
        request.setDescription("Updated");
        request.setDefaultCurrency("USD");

        when(groupService.updateGroup(eq(1L), eq(1L), any()))
                .thenThrow(new NotGroupAdminException("Only group admins can perform this action"));

        mockMvc.perform(put("/api/v1/groups/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteGroup_returns204() throws Exception {
        stubValidAccessToken(1L);

        mockMvc.perform(delete("/api/v1/groups/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteGroup_returns409_whenBalancesAreUnsettled() throws Exception {
        stubValidAccessToken(1L);
        doThrow(new UnsettledBalancesException("Settle all balances before deleting this group"))
                .when(groupService).deleteGroup(1L, 1L);

        mockMvc.perform(delete("/api/v1/groups/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("UNSETTLED_BALANCES"));
    }
}
