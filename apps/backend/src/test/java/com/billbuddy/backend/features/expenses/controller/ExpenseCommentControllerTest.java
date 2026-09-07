package com.billbuddy.backend.features.expenses.controller;

import com.billbuddy.backend.config.SecurityConfig;
import com.billbuddy.backend.exception.ExpenseCommentNotFoundException;
import com.billbuddy.backend.exception.NotExpenseCommentOwnerException;
import com.billbuddy.backend.features.auth.security.JwtService;
import com.billbuddy.backend.features.expenses.dto.request.CreateExpenseCommentRequest;
import com.billbuddy.backend.features.expenses.dto.response.ExpenseCommentResponse;
import com.billbuddy.backend.features.expenses.service.ExpenseCommentService;
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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpenseCommentController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ExpenseCommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExpenseCommentService expenseCommentService;

    @MockBean
    private JwtService jwtService;

    private static final String VALID_TOKEN = "valid-access-token";

    private void stubValidAccessToken(Long userId) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(String.valueOf(userId));
        when(claims.get("email", String.class)).thenReturn("jane@example.com");
        when(jwtService.parseAndValidate(VALID_TOKEN)).thenReturn(claims);
    }

    private ExpenseCommentResponse sampleResponse() {
        return new ExpenseCommentResponse(1L, 100L, "Looks right to me", 1L, "Jane Doe", LocalDateTime.now());
    }

    private CreateExpenseCommentRequest buildRequest(String body) {
        CreateExpenseCommentRequest request = new CreateExpenseCommentRequest();
        request.setBody(body);
        return request;
    }

    @Test
    void createComment_returns201_whenValid() throws Exception {
        stubValidAccessToken(1L);
        when(expenseCommentService.createComment(org.mockito.ArgumentMatchers.eq(100L), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/expenses/100/comments")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Looks right to me"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Looks right to me"));
    }

    @Test
    void createComment_returns400_whenBodyBlank() throws Exception {
        stubValidAccessToken(1L);

        mockMvc.perform(post("/api/v1/expenses/100/comments")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest(""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createComment_returns403_whenNoAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/expenses/100/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Hi"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listComments_returns200() throws Exception {
        stubValidAccessToken(1L);
        when(expenseCommentService.listComments(100L, 1L)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/expenses/100/comments")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void deleteComment_returns204() throws Exception {
        stubValidAccessToken(1L);

        mockMvc.perform(delete("/api/v1/expenses/100/comments/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteComment_returns403_whenServiceThrowsNotOwner() throws Exception {
        stubValidAccessToken(2L);
        doThrow(new NotExpenseCommentOwnerException("Only the comment's author or a group admin can delete it"))
                .when(expenseCommentService).deleteComment(100L, 1L, 2L);

        mockMvc.perform(delete("/api/v1/expenses/100/comments/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteComment_returns404_whenServiceThrowsNotFound() throws Exception {
        stubValidAccessToken(1L);
        doThrow(new ExpenseCommentNotFoundException("Comment not found"))
                .when(expenseCommentService).deleteComment(100L, 1L, 1L);

        mockMvc.perform(delete("/api/v1/expenses/100/comments/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound());
    }
}
