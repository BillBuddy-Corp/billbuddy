package com.billbuddy.backend.features.auth.service;

import com.billbuddy.backend.exception.InvalidCurrencyException;
import com.billbuddy.backend.exception.NotFileOwnerException;
import com.billbuddy.backend.exception.UserNotFoundException;
import com.billbuddy.backend.features.auth.dto.request.UpdateProfileRequest;
import com.billbuddy.backend.features.auth.dto.response.UserProfileResponse;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.storage.model.StoredFile;
import com.billbuddy.backend.features.storage.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileService fileService;

    @Mock
    private MobileOtpService mobileOtpService;

    @InjectMocks
    private UserService userService;

    private User buildUser(Long id) {
        User user = User.signupWithEmail("user" + id + "@example.com", "hashed-password", "User " + id, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private StoredFile buildStoredFile(Long id, User uploadedBy) {
        StoredFile file = StoredFile.create(uploadedBy, "image/png", 1024L, "key.png");
        ReflectionTestUtils.setField(file, "id", id);
        return file;
    }

    // ===================== GET =====================

    @Test
    void getProfile_returnsResponse_whenUserExists() {
        User user = buildUser(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.getProfile(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getFullName()).isEqualTo("User 1");
        assertThat(response.getProfilePicUrl()).isNull();
        assertThat(response.isEmailVerified()).isFalse();
    }

    @Test
    void getProfile_returnsEmailVerifiedTrue_whenEmailVerifiedAtIsSet() {
        User user = buildUser(1L);
        ReflectionTestUtils.setField(user, "emailVerifiedAt", java.time.LocalDateTime.now());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.getProfile(1L);

        assertThat(response.isEmailVerified()).isTrue();
    }

    @Test
    void getProfile_throwsUserNotFound_whenMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(1L))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ===================== UPDATE =====================

    @Test
    void updateProfile_updatesFullNameAndCurrency_whenNoProfilePic() {
        User user = buildUser(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Updated Name");
        request.setDefaultCurrency("usd");

        UserProfileResponse response = userService.updateProfile(1L, request);

        assertThat(response.getFullName()).isEqualTo("Updated Name");
        assertThat(response.getDefaultCurrency()).isEqualTo("USD");
        assertThat(response.getProfilePicUrl()).isNull();
        verifyNoInteractions(fileService);
    }

    @Test
    void updateProfile_attachesProfilePic_whenProfilePicFileIdProvided() {
        User user = buildUser(1L);
        StoredFile file = buildStoredFile(200L, user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fileService.requireOwnedFile(200L, 1L)).thenReturn(file);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Jane Doe");
        request.setProfilePicFileId(200L);
        request.setDefaultCurrency("INR");

        UserProfileResponse response = userService.updateProfile(1L, request);

        assertThat(response.getProfilePicUrl()).isEqualTo("/api/v1/files/200");
        verify(fileService).requireOwnedFile(200L, 1L);
    }

    @Test
    void updateProfile_propagatesNotFileOwner_whenFileServiceRejectsIt() {
        User user = buildUser(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fileService.requireOwnedFile(200L, 1L))
                .thenThrow(new NotFileOwnerException("You can only attach files you uploaded yourself"));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Jane Doe");
        request.setProfilePicFileId(200L);
        request.setDefaultCurrency("INR");

        assertThatThrownBy(() -> userService.updateProfile(1L, request))
                .isInstanceOf(NotFileOwnerException.class);
    }

    @Test
    void updateProfile_throwsInvalidCurrency_whenCurrencyIsNotARealCode() {
        User user = buildUser(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Jane Doe");
        request.setDefaultCurrency("ZZZ");

        assertThatThrownBy(() -> userService.updateProfile(1L, request))
                .isInstanceOf(InvalidCurrencyException.class);
    }

    @Test
    void updateProfile_throwsUserNotFound_whenMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Jane Doe");
        request.setDefaultCurrency("INR");

        assertThatThrownBy(() -> userService.updateProfile(1L, request))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ===================== MOBILE NUMBER =====================

    @Test
    void updateProfile_sendsOtp_whenMobileNumberChanges() {
        User user = buildUser(1L); // no mobile number set yet
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Jane Doe");
        request.setDefaultCurrency("INR");
        request.setMobileNumber("+919876543210");

        UserProfileResponse response = userService.updateProfile(1L, request);

        assertThat(response.getMobileNumber()).isEqualTo("+919876543210");
        assertThat(response.isMobileVerified()).isFalse();
        verify(mobileOtpService).sendOtpIfMobileNumberPresent(user);
    }

    @Test
    void updateProfile_doesNotSendOtp_whenMobileNumberUnchanged() {
        User user = User.signupWithEmail("user1@example.com", "hashed-password", "User 1", "+919876543210");
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Jane Doe");
        request.setDefaultCurrency("INR");
        request.setMobileNumber("+919876543210"); // same number

        userService.updateProfile(1L, request);

        verifyNoInteractions(mobileOtpService);
    }

    @Test
    void updateProfile_resetsMobileVerifiedStatus_whenNumberChanges() {
        User user = User.signupWithEmail("user1@example.com", "hashed-password", "User 1", "+919876543210");
        ReflectionTestUtils.setField(user, "id", 1L);
        user.setMobileVerifiedAt(java.time.LocalDateTime.now());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Jane Doe");
        request.setDefaultCurrency("INR");
        request.setMobileNumber("+919999999999"); // different number

        UserProfileResponse response = userService.updateProfile(1L, request);

        assertThat(response.isMobileVerified()).isFalse();
        verify(mobileOtpService).sendOtpIfMobileNumberPresent(user);
    }

    @Test
    void updateProfile_setsFcmToken_whenProvided() {
        User user = buildUser(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("User 1");
        request.setDefaultCurrency("INR");
        request.setFcmToken("device-token-abc");

        userService.updateProfile(1L, request);

        assertThat(user.getFcmToken()).isEqualTo("device-token-abc");
    }

    @Test
    void updateProfile_preservesFcmToken_whenOmitted() {
        User user = buildUser(1L);
        user.updateProfile(user.getFullName(), null, user.getDefaultCurrency(), null, "already-registered-token");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Updated Name");
        request.setDefaultCurrency("INR");
        // fcmToken deliberately left null, simulating a routine profile edit unrelated to push registration

        userService.updateProfile(1L, request);

        assertThat(user.getFcmToken()).isEqualTo("already-registered-token");
    }
}
