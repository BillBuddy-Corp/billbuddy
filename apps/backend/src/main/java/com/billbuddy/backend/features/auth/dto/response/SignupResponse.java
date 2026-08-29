package com.billbuddy.backend.features.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class SignupResponse {

    private final Long id;

    private final String email;

    private final String fullName;

    private final LocalDateTime createdAt;

}
