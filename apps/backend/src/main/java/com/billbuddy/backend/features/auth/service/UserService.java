package com.billbuddy.backend.features.auth.service;

import com.billbuddy.backend.common.CurrencyUtil;
import com.billbuddy.backend.exception.UserNotFoundException;
import com.billbuddy.backend.features.auth.dto.request.UpdateProfileRequest;
import com.billbuddy.backend.features.auth.dto.response.UserProfileResponse;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.storage.model.StoredFile;
import com.billbuddy.backend.features.storage.service.FileService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final FileService fileService;

    public UserService(UserRepository userRepository, FileService fileService) {
        this.userRepository = userRepository;
        this.fileService = fileService;
    }

    @Transactional
    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return toResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        String currency = CurrencyUtil.normalize(request.getDefaultCurrency());
        StoredFile profilePicFile = request.getProfilePicFileId() == null
                ? null
                : fileService.requireOwnedFile(request.getProfilePicFileId(), userId);

        user.updateProfile(request.getFullName(), profilePicFile, currency);

        return toResponse(user);
    }

    private UserProfileResponse toResponse(User user) {
        String profilePicUrl = user.getProfilePicFile() == null
                ? null
                : "/api/v1/files/" + user.getProfilePicFile().getId();

        return new UserProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getMobileNumber(),
                profilePicUrl,
                user.getDefaultCurrency(),
                user.getCreatedAt()
        );
    }
}
