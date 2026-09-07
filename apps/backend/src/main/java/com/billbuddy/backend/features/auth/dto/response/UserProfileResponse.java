package com.billbuddy.backend.features.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class UserProfileResponse {

    private Long id;
    private String fullName;
    private String email;
    private String pendingEmail;
    private String mobileNumber;
    private boolean mobileVerified;
    private String profilePicUrl;
    private String defaultCurrency;
    private LocalDateTime createdAt;
}
