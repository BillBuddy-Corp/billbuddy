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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private AuthTokenService authTokenService;

    @Mock
    private MobileOtpService mobileOtpService;

    @InjectMocks
    private AuthService authService;

    private static final int REFRESH_TOKEN_EXPIRY_DAYS = 30;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiryDays", REFRESH_TOKEN_EXPIRY_DAYS);
    }

    private User buildUser(Long id, String email, String rawPasswordHash) {
        User user = User.signupWithEmail(email, rawPasswordHash, "Jane Doe", "+919876543210");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    // ===================== SIGNUP =====================

    @Test
    void signup_savesNewUser_whenEmailNotTaken() {
        SignupRequest request = new SignupRequest();
        request.setEmail("Jane@Example.com");
        request.setFullName("Jane Doe");
        request.setMobileNumber("+919876543210");
        request.setPassword("password123");

        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        SignupResponse response = authService.signup(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("jane@example.com");
        assertThat(response.getFullName()).isEqualTo("Jane Doe");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed-password");
        verify(mobileOtpService).sendOtpIfMobileNumberPresent(userCaptor.getValue());
    }

    @Test
    void signup_throwsUserAlreadyExists_whenEmailAlreadyRegistered() {
        SignupRequest request = new SignupRequest();
        request.setEmail("jane@example.com");
        request.setFullName("Jane Doe");
        request.setPassword("password123");

        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessage("Email already registered");

        verify(userRepository, never()).save(any());
    }

    // ===================== LOGIN =====================

    @Test
    void login_returnsTokensAndRevokesPriorSessionOnSameDevice_whenCredentialsValid() {
        User user = buildUser(1L, "jane@example.com", "hashed-password");

        LoginRequest request = new LoginRequest();
        request.setEmail("jane@example.com");
        request.setPassword("password123");
        request.setDeviceId("device-abc");
        request.setDeviceName("iPhone 15");

        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);
        when(jwtService.generateAccessToken(1L, "jane@example.com")).thenReturn("access-token");
        when(jwtService.generateRefreshToken(1L)).thenReturn("raw-refresh-token");
        when(jwtService.hashToken("raw-refresh-token")).thenReturn("hashed-refresh-token");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");

        LoginResponse response = authService.login(request, httpRequest);

        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("raw-refresh-token");
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("jane@example.com");

        verify(refreshTokenRepository).revokeActiveByUserIdAndDeviceId(1L, "device-abc");

        ArgumentCaptor<RefreshToken> sessionCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(sessionCaptor.capture());
        RefreshToken savedSession = sessionCaptor.getValue();
        assertThat(savedSession.getTokenHash()).isEqualTo("hashed-refresh-token");
        assertThat(savedSession.getDeviceId()).isEqualTo("device-abc");
        assertThat(savedSession.getDeviceName()).isEqualTo("iPhone 15");
        assertThat(savedSession.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(savedSession.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(savedSession.isRevoked()).isFalse();
    }

    @Test
    void login_prefersXForwardedForHeader_overRemoteAddr() {
        User user = buildUser(1L, "jane@example.com", "hashed-password");

        LoginRequest request = new LoginRequest();
        request.setEmail("jane@example.com");
        request.setPassword("password123");
        request.setDeviceId("device-abc");

        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);
        when(jwtService.generateAccessToken(any(), anyString())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("raw-refresh-token");
        when(jwtService.hashToken(anyString())).thenReturn("hashed-refresh-token");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 70.41.3.18");
        when(httpRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");

        authService.login(request, httpRequest);

        ArgumentCaptor<RefreshToken> sessionCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getIpAddress()).isEqualTo("203.0.113.5");
    }

    @Test
    void login_throwsUserNotFound_whenEmailDoesNotExist() {
        LoginRequest request = new LoginRequest();
        request.setEmail("ghost@example.com");
        request.setPassword("password123");
        request.setDeviceId("device-abc");

        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request, httpRequest))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("Invalid email or password");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_throwsInvalidCredentials_whenPasswordWrong() {
        User user = buildUser(1L, "jane@example.com", "hashed-password");

        LoginRequest request = new LoginRequest();
        request.setEmail("jane@example.com");
        request.setPassword("wrong-password");
        request.setDeviceId("device-abc");

        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request, httpRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(refreshTokenRepository, never()).save(any());
    }

    // ===================== REFRESH =====================

    @Test
    void refreshAccessToken_rotatesToken_whenValidAndNotExpired() {
        User user = buildUser(1L, "jane@example.com", "hashed-password");
        RefreshToken storedToken = RefreshToken.create(
                user, "old-hash", LocalDateTime.now().plusDays(10),
                "device-abc", "iPhone 15", "127.0.0.1", "Mozilla/5.0"
        );

        when(jwtService.hashToken("raw-old-token")).thenReturn("old-hash");
        when(refreshTokenRepository.findByTokenHashAndDeviceId("old-hash", "device-abc"))
                .thenReturn(Optional.of(storedToken));
        when(jwtService.generateAccessToken(1L, "jane@example.com")).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(1L)).thenReturn("raw-new-token");
        when(jwtService.hashToken("raw-new-token")).thenReturn("new-hash");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");

        LoginResponse response = authService.refreshAccessToken("raw-old-token", "device-abc", httpRequest);

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("raw-new-token");
        assertThat(storedToken.isRevoked()).isTrue();

        ArgumentCaptor<RefreshToken> newSessionCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(newSessionCaptor.capture());
        RefreshToken newSession = newSessionCaptor.getValue();
        assertThat(newSession.getTokenHash()).isEqualTo("new-hash");
        assertThat(newSession.getDeviceId()).isEqualTo("device-abc");
        assertThat(newSession.getDeviceName()).isEqualTo("iPhone 15");
    }

    @Test
    void refreshAccessToken_throwsInvalidCredentials_whenTokenNotFound() {
        when(jwtService.hashToken("raw-token")).thenReturn("some-hash");
        when(refreshTokenRepository.findByTokenHashAndDeviceId("some-hash", "device-abc"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshAccessToken("raw-token", "device-abc", httpRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid refresh token");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refreshAccessToken_throwsInvalidCredentials_whenTokenRevoked() {
        User user = buildUser(1L, "jane@example.com", "hashed-password");
        RefreshToken storedToken = RefreshToken.create(
                user, "old-hash", LocalDateTime.now().plusDays(10),
                "device-abc", "iPhone 15", "127.0.0.1", "Mozilla/5.0"
        );
        storedToken.revoke();

        when(jwtService.hashToken("raw-token")).thenReturn("old-hash");
        when(refreshTokenRepository.findByTokenHashAndDeviceId("old-hash", "device-abc"))
                .thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refreshAccessToken("raw-token", "device-abc", httpRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Refresh token revoked");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refreshAccessToken_throwsInvalidCredentials_whenTokenExpired() {
        User user = buildUser(1L, "jane@example.com", "hashed-password");
        RefreshToken storedToken = RefreshToken.create(
                user, "old-hash", LocalDateTime.now().minusDays(1),
                "device-abc", "iPhone 15", "127.0.0.1", "Mozilla/5.0"
        );

        when(jwtService.hashToken("raw-token")).thenReturn("old-hash");
        when(refreshTokenRepository.findByTokenHashAndDeviceId("old-hash", "device-abc"))
                .thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refreshAccessToken("raw-token", "device-abc", httpRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Refresh token expired");

        verify(refreshTokenRepository, never()).save(any());
    }

    // ===================== LOGOUT =====================

    @Test
    void logout_revokesToken_whenFound() {
        User user = buildUser(1L, "jane@example.com", "hashed-password");
        RefreshToken storedToken = RefreshToken.create(
                user, "hash", LocalDateTime.now().plusDays(10),
                "device-abc", "iPhone 15", "127.0.0.1", "Mozilla/5.0"
        );

        when(jwtService.hashToken("raw-token")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHashAndDeviceId("hash", "device-abc"))
                .thenReturn(Optional.of(storedToken));

        authService.logout("raw-token", "device-abc");

        assertThat(storedToken.isRevoked()).isTrue();
    }

    @Test
    void logout_throwsInvalidCredentials_whenTokenNotFound() {
        when(jwtService.hashToken("raw-token")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHashAndDeviceId("hash", "device-abc"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.logout("raw-token", "device-abc"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid refresh token");
    }

    // ===================== LOGOUT ALL DEVICES =====================

    @Test
    void logoutAllDevices_revokesEverySessionForUser() {
        when(refreshTokenRepository.revokeAllByUserId(1L)).thenReturn(3);

        authService.logoutAllDevices(1L);

        verify(refreshTokenRepository, times(1)).revokeAllByUserId(1L);
    }

    // ===================== SESSIONS =====================

    @Test
    void listSessions_marksMatchingDeviceAsCurrent() {
        User user = buildUser(1L, "jane@example.com", "hashed-password");

        RefreshToken sessionOnThisDevice = RefreshToken.create(
                user, "hash-1", LocalDateTime.now().plusDays(10),
                "device-abc", "iPhone 15", "127.0.0.1", "Mozilla/5.0"
        );
        sessionOnThisDevice.setId(10L);

        RefreshToken sessionOnOtherDevice = RefreshToken.create(
                user, "hash-2", LocalDateTime.now().plusDays(10),
                "device-xyz", "Pixel 8", "127.0.0.2", "Mozilla/5.0"
        );
        sessionOnOtherDevice.setId(11L);

        when(refreshTokenRepository.findActiveSessions(1L))
                .thenReturn(List.of(sessionOnThisDevice, sessionOnOtherDevice));

        List<SessionResponse> sessions = authService.listSessions(1L, "device-abc");

        assertThat(sessions).hasSize(2);
        assertThat(sessions)
                .filteredOn(s -> s.getDeviceId().equals("device-abc"))
                .singleElement()
                .satisfies(s -> assertThat(s.isCurrent()).isTrue());
        assertThat(sessions)
                .filteredOn(s -> s.getDeviceId().equals("device-xyz"))
                .singleElement()
                .satisfies(s -> assertThat(s.isCurrent()).isFalse());
    }
}
