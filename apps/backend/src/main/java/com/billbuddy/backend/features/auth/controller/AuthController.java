package com.billbuddy.backend.features.auth.controller;

import com.billbuddy.backend.features.auth.dto.request.ChangePasswordRequest;
import com.billbuddy.backend.features.auth.dto.request.ForgotPasswordRequest;
import com.billbuddy.backend.features.auth.dto.request.GoogleSignInRequest;
import com.billbuddy.backend.features.auth.dto.request.LoginRequest;
import com.billbuddy.backend.features.auth.dto.request.LogoutRequest;
import com.billbuddy.backend.features.auth.dto.request.RefreshTokenRequest;
import com.billbuddy.backend.features.auth.dto.request.ResetPasswordRequest;
import com.billbuddy.backend.features.auth.dto.request.SignupRequest;
import com.billbuddy.backend.features.auth.dto.request.VerifyEmailRequest;
import com.billbuddy.backend.features.auth.dto.request.VerifyMobileRequest;
import com.billbuddy.backend.features.auth.dto.response.LoginResponse;
import com.billbuddy.backend.features.auth.dto.response.SignupResponse;
import com.billbuddy.backend.features.auth.service.AuthService;
import com.billbuddy.backend.features.auth.service.AuthTokenService;
import com.billbuddy.backend.features.auth.service.MobileOtpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication management APIs")
public class AuthController {

    private final AuthService authService;
    private final AuthTokenService authTokenService;
    private final MobileOtpService mobileOtpService;

    public AuthController(AuthService authService, AuthTokenService authTokenService, MobileOtpService mobileOtpService) {
        this.authService = authService;
        this.authTokenService = authTokenService;
        this.mobileOtpService = mobileOtpService;
    }

    @PostMapping("/signup")
    @Operation(
            summary = "Register new user",
            description = "Create a new user account with email and password"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "409", description = "Email already exists")
    })
    public ResponseEntity<SignupResponse> signup(
            @Valid @RequestBody SignupRequest signupRequest) {

        SignupResponse response = authService.signup(signupRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @PostMapping("/login")
    @Operation(
            summary = "Login user",
            description = "Authenticate user and return JWT tokens with device tracking"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {

        LoginResponse response = authService.login(request,httpRequest);
        System.out.println("LOGIN RESPONSE CLASS = " + response.getClass());
        System.out.println("ACCESS TOKEN = " + response.getAccessToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/google")
    @Operation(
            summary = "Sign in with Google",
            description = "Verifies a Google ID token obtained by the client's own Google Sign-In SDK, then logs into an existing Google-linked account or creates a new one, issuing our own JWT tokens"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sign-in successful"),
            @ApiResponse(responseCode = "401", description = "Invalid Google token, or the Google account's email isn't verified"),
            @ApiResponse(responseCode = "409", description = "Email is already registered with a password")
    })
    public ResponseEntity<LoginResponse> signInWithGoogle(
            @Valid @RequestBody GoogleSignInRequest request,
            HttpServletRequest httpRequest
    ) {
        LoginResponse response = authService.signInWithGoogle(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refreshtoken")
    @Operation(
            summary = "Refresh access token",
            description = "Generate new access token using refresh token (token rotation)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    public ResponseEntity<LoginResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest
    ) {
        LoginResponse response = authService.refreshAccessToken(request.getRefreshToken(),request.getDeviceId(),httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Logout from current device",
            description = "Revoke refresh token for the current device",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logout successful"),
            @ApiResponse(responseCode = "401", description = "Invalid token")
    })
    public ResponseEntity<Map<String, String>> logout(
            @Valid @RequestBody LogoutRequest request
    ) {
        authService.logout(request.getRefreshToken(),request.getDeviceId());
        return ResponseEntity.ok(Map.of("message", "Logout successful"));
    }

    @PostMapping("/logout-all")
    @Operation(
            summary = "Logout from all devices",
            description = "Revoke all active refresh tokens for the authenticated user",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logged out from all devices"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Map<String, String>> logoutAll(
            @AuthenticationPrincipal Long userId
    ) {
        authService.logoutAllDevices(userId);
        return ResponseEntity.ok(Map.of("message", "Logged out from all devices"));
    }

    @GetMapping("/sessions")
    @Operation(
            summary = "List active sessions",
            description = "Get all active sessions for the authenticated user",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sessions retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
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

    @PostMapping("/forgot-password")
    @Operation(
            summary = "Request a password reset",
            description = "Emails a reset link if the address is registered. Always returns success to avoid revealing whether an email exists"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Request accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        authTokenService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "If that email is registered, a reset link has been sent"));
    }

    @PostMapping("/reset-password")
    @Operation(
            summary = "Reset password with a token",
            description = "Consumes a password reset token, sets a new password, and revokes all active sessions"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid, expired, or already-used token")
    })
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        authTokenService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset successful"));
    }

    @PostMapping("/change-password")
    @Operation(
            summary = "Change password",
            description = "Changes the authenticated user's password and revokes all active sessions",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password changed successfully"),
            @ApiResponse(responseCode = "401", description = "Current password is incorrect")
    })
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authTokenService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    @PostMapping("/verify-email")
    @Operation(
            summary = "Verify email with a token",
            description = "Consumes an email verification token and marks the email as verified"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email verified successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid, expired, or already-used token")
    })
    public ResponseEntity<Map<String, String>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request
    ) {
        authTokenService.verifyEmail(request.getToken());
        return ResponseEntity.ok(Map.of("message", "Email verified successfully"));
    }

    @PostMapping("/verify-email/resend")
    @Operation(
            summary = "Resend the email verification link",
            description = "No-ops with a friendly message if the email is already verified",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verification email sent, or already verified"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Map<String, String>> resendVerification(
            @AuthenticationPrincipal Long userId
    ) {
        String message = authTokenService.resendVerificationEmail(userId);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/verify-mobile")
    @Operation(
            summary = "Verify mobile number with an OTP code",
            description = "Consumes a 6-digit code sent by SMS and marks the mobile number as verified",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Mobile number verified successfully"),
            @ApiResponse(responseCode = "400", description = "Incorrect, expired, or already-used code")
    })
    public ResponseEntity<Map<String, String>> verifyMobile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody VerifyMobileRequest request
    ) {
        mobileOtpService.verifyMobile(userId, request.getCode());
        return ResponseEntity.ok(Map.of("message", "Mobile number verified successfully"));
    }

    @PostMapping("/verify-mobile/resend")
    @Operation(
            summary = "Resend the mobile verification code",
            description = "No-ops with a friendly message if already verified. Fails if no mobile number is on file",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verification code sent, or already verified"),
            @ApiResponse(responseCode = "400", description = "No mobile number on file"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Map<String, String>> resendMobileVerification(
            @AuthenticationPrincipal Long userId
    ) {
        String message = mobileOtpService.resendOtp(userId);
        return ResponseEntity.ok(Map.of("message", message));
    }

}
