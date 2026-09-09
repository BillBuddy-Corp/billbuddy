package com.billbuddy.backend.features.friends.controller;

import com.billbuddy.backend.config.SecurityConfig;
import com.billbuddy.backend.features.auth.security.JwtService;
import com.billbuddy.backend.features.friends.dto.request.AddFriendRequest;
import com.billbuddy.backend.features.friends.dto.response.FriendResponse;
import com.billbuddy.backend.features.friends.service.FriendshipService;
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

@WebMvcTest(FriendController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class FriendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FriendshipService friendshipService;

    @MockBean
    private JwtService jwtService;

    private static final String VALID_TOKEN = "valid-access-token";

    private void stubValidAccessToken(Long userId) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(String.valueOf(userId));
        when(claims.get("email", String.class)).thenReturn("jane@example.com");
        when(jwtService.parseAndValidate(VALID_TOKEN)).thenReturn(claims);
    }

    private FriendResponse sampleFriendResponse() {
        return new FriendResponse(2L, "Jane Doe", "friend@example.com", LocalDateTime.now());
    }

    @Test
    void addFriend_returns201() throws Exception {
        stubValidAccessToken(1L);
        AddFriendRequest request = new AddFriendRequest();
        request.setEmail("friend@example.com");
        when(friendshipService.addFriend(1L, "friend@example.com")).thenReturn(sampleFriendResponse());

        mockMvc.perform(post("/api/v1/friends")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.email").value("friend@example.com"));
    }

    @Test
    void addFriend_returns400_whenEmailInvalid() throws Exception {
        stubValidAccessToken(1L);
        AddFriendRequest request = new AddFriendRequest();
        request.setEmail("not-an-email");

        mockMvc.perform(post("/api/v1/friends")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listFriends_returns200() throws Exception {
        stubValidAccessToken(1L);
        when(friendshipService.listFriends(1L)).thenReturn(List.of(sampleFriendResponse()));

        mockMvc.perform(get("/api/v1/friends")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void removeFriend_returns204() throws Exception {
        stubValidAccessToken(1L);

        mockMvc.perform(delete("/api/v1/friends/2")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNoContent());
    }
}
