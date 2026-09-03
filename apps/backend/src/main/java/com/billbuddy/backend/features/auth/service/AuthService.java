package com.billbuddy.backend.features.auth.service;

import com.billbuddy.backend.exception.InvalidCredentialsException;
import com.billbuddy.backend.exception.UserAlreadyExistsException;
import com.billbuddy.backend.exception.UserNotFoundException;
import com.billbuddy.backend.features.auth.dto.request.LoginRequest;
import com.billbuddy.backend.features.auth.dto.request.SignupRequest;
import com.billbuddy.backend.features.auth.dto.response.LoginResponse;
import com.billbuddy.backend.features.auth.dto.response.SessionResponse;
import com.billbuddy.backend.features.auth.dto.response.SignupResponse;
import com.billbuddy.backend.features.auth.model.RefreshToken;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.RefreshTokenRepository;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.auth.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthTokenService authTokenService;

    @Value("${jwt.refresh-token-expiry-days}")
    private int refreshTokenExpiryDays;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthTokenService authTokenService
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authTokenService = authTokenService;
    }

    // ===================== SIGNUP =====================

    @Transactional
    public SignupResponse signup(SignupRequest request) {

        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Email already registered");
        }

        User user = User.signupWithEmail(
                email,
                passwordEncoder.encode(request.getPassword()),
                request.getFullName(),
                request.getMobileNumber()
        );

        try {
            User saved = userRepository.save(user);
            log.info("Signup successful, userId={}", saved.getId());

            authTokenService.sendVerificationEmail(saved);

            return new SignupResponse(
                    saved.getId(),
                    saved.getEmail(),
                    saved.getFullName(),
                    saved.getCreatedAt()
            );

        } catch (DataIntegrityViolationException ex) {
            throw new UserAlreadyExistsException("Email already registered");
        }
    }

    // ===================== LOGIN =====================

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {

        String email = normalizeEmail(request.getEmail());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        user.setLastLoginAt(LocalDateTime.now());

        // Tokens
        String accessToken =
                jwtService.generateAccessToken(user.getId(), user.getEmail());

        String rawRefreshToken =
                jwtService.generateRefreshToken(user.getId());

        String refreshTokenHash =
                jwtService.hashToken(rawRefreshToken);

        // Device metadata
        String ipAddress = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        // One active session per device
        refreshTokenRepository
                .revokeActiveByUserIdAndDeviceId(user.getId(), request.getDeviceId());

        RefreshToken session = RefreshToken.create(
                user,
                refreshTokenHash,
                LocalDateTime.now().plusDays(refreshTokenExpiryDays),
                request.getDeviceId(),
                request.getDeviceName(),
                ipAddress,
                userAgent
        );

        refreshTokenRepository.save(session);
        userRepository.save(user);

        log.info("Login successful, userId={}, deviceId={}",
                user.getId(), request.getDeviceId());

        return new LoginResponse(
                "Bearer",
                accessToken,
                rawRefreshToken,
                user.getId(),
                user.getEmail(),
                user.getFullName()
        );
    }

    // ===================== REFRESH =====================

    @Transactional
    public LoginResponse refreshAccessToken(
            String rawRefreshToken,
            String deviceId,
            HttpServletRequest request
    ) {

        String tokenHash = jwtService.hashToken(rawRefreshToken);

        RefreshToken storedToken =
                refreshTokenRepository
                        .findByTokenHashAndDeviceId(tokenHash, deviceId)
                        .orElseThrow(() ->
                                new InvalidCredentialsException("Invalid refresh token"));

        if (storedToken.isRevoked()) {
            throw new InvalidCredentialsException("Refresh token revoked");
        }

        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidCredentialsException("Refresh token expired");
        }

        // Rotate
        storedToken.revoke();
        storedToken.setLastUsedAt(LocalDateTime.now());

        User user = storedToken.getUser();

        String newAccessToken =
                jwtService.generateAccessToken(user.getId(), user.getEmail());

        String newRawRefreshToken =
                jwtService.generateRefreshToken(user.getId());

        RefreshToken newSession = RefreshToken.create(
                user,
                jwtService.hashToken(newRawRefreshToken),
                LocalDateTime.now().plusDays(refreshTokenExpiryDays),
                storedToken.getDeviceId(),
                storedToken.getDeviceName(),
                extractClientIp(request),
                request.getHeader("User-Agent")
        );

        refreshTokenRepository.save(newSession);

        log.info("Refresh token rotated, userId={}, deviceId={}",
                user.getId(), deviceId);

        return new LoginResponse(
                "Bearer",
                newAccessToken,
                newRawRefreshToken,
                user.getId(),
                user.getEmail(),
                user.getFullName()
        );
    }

    // ===================== LOGOUT (ONE DEVICE) =====================

    @Transactional
    public void logout(String rawRefreshToken, String deviceId) {

        String tokenHash = jwtService.hashToken(rawRefreshToken);

        RefreshToken token =
                refreshTokenRepository
                        .findByTokenHashAndDeviceId(tokenHash, deviceId)
                        .orElseThrow(() ->
                                new InvalidCredentialsException("Invalid refresh token"));

        token.revoke();

        log.info("Logout successful, userId={}, deviceId={}",
                token.getUser().getId(), deviceId);
    }

    // ===================== LOGOUT ALL DEVICES =====================


    @Transactional
    public void logoutAllDevices(Long userId) {

        int revokedCount = refreshTokenRepository.revokeAllByUserId(userId);

        log.info("Logout-all successful, userId={}, sessionsRevoked={}",
                userId, revokedCount);
    }

    @Transactional
    public List<SessionResponse> listSessions(
            Long userId,
            String currentDeviceId
    ) {
        List<RefreshToken> sessions =
                refreshTokenRepository.findActiveSessions(userId);

        return sessions.stream()
                .map(rt -> new SessionResponse(
                        rt.getId(),
                        rt.getDeviceId(),
                        rt.getDeviceName(),
                        rt.getIpAddress(),
                        rt.getUserAgent(),
                        rt.getCreatedAt(),
                        rt.getExpiresAt(),
                        rt.getDeviceId().equals(currentDeviceId)
                ))
                .toList();
    }



    // ===================== HELPERS =====================

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
