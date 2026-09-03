package com.billbuddy.backend.features.storage.controller;

import com.billbuddy.backend.config.SecurityConfig;
import com.billbuddy.backend.exception.NotFileOwnerException;
import com.billbuddy.backend.features.auth.security.JwtService;
import com.billbuddy.backend.features.storage.dto.response.FileResponse;
import com.billbuddy.backend.features.storage.service.FileService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileService fileService;

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
    void upload_returns201_whenValid() throws Exception {
        stubValidAccessToken(1L);
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "hello".getBytes());

        when(fileService.upload(eq(1L), any())).thenReturn(
                new FileResponse(100L, "/api/v1/files/100", "image/png", 5L, LocalDateTime.now())
        );

        mockMvc.perform(multipart("/api/v1/files")
                        .file(file)
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.url").value("/api/v1/files/100"));
    }

    @Test
    void upload_returns403_whenNoAuthorizationHeader() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "hello".getBytes());

        mockMvc.perform(multipart("/api/v1/files").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    void download_returns200_withFileBytes() throws Exception {
        stubValidAccessToken(1L);
        when(fileService.getFileForViewing(100L, 1L))
                .thenReturn(new FileService.FileDownload(new ByteArrayResource("hello".getBytes()), "image/png"));

        mockMvc.perform(get("/api/v1/files/100")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void download_returns403_whenServiceThrowsNotFileOwner() throws Exception {
        stubValidAccessToken(2L);
        when(fileService.getFileForViewing(100L, 2L))
                .thenThrow(new NotFileOwnerException("You can only view files you uploaded until they're attached to something"));

        mockMvc.perform(get("/api/v1/files/100")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isForbidden());
    }
}
