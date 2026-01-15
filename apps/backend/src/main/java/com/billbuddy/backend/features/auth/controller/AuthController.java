package com.billbuddy.backend.features.auth.controller;

import com.billbuddy.backend.features.auth.dto.request.LoginRequest;
import com.billbuddy.backend.features.auth.dto.request.LogoutRequest;
import com.billbuddy.backend.features.auth.dto.request.RefreshTokenRequest;
import com.billbuddy.backend.features.auth.dto.request.SignupRequest;
import com.billbuddy.backend.features.auth.dto.response.LoginResponse;
import com.billbuddy.backend.features.auth.dto.response.SignupResponse;
import com.billbuddy.backend.features.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(
            @Valid @RequestBody SignupRequest signupRequest) {

        SignupResponse response = authService.signup(signupRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {

        LoginResponse response = authService.login(request,httpRequest);
        System.out.println("LOGIN RESPONSE CLASS = " + response.getClass());
        System.out.println("ACCESS TOKEN = " + response.getAccessToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refreshtoken")
    public ResponseEntity<LoginResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest
    ) {
        LoginResponse response = authService.refreshAccessToken(request.getRefreshToken(),request.getDeviceId(),httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @Valid @RequestBody LogoutRequest request
    ) {
        authService.logout(request.getRefreshToken(),request.getDeviceId());
        return ResponseEntity.ok(Map.of("message", "Logout successful"));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Map<String, String>> logoutAll(
            @AuthenticationPrincipal Long userId
    ) {
        authService.logoutAllDevices(userId);
        return ResponseEntity.ok(Map.of("message", "Logged out from all devices"));
    }

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> sessions(
            @AuthenticationPrincipal Long userId,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        return ResponseEntity.ok(
                Map.of(
                        "sessions",
                        authService.listSessions(userId, deviceId)
                )
        );
    }


}
