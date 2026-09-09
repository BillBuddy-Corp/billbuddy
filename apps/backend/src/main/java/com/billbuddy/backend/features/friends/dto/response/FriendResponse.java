package com.billbuddy.backend.features.friends.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class FriendResponse {

    private Long userId;
    private String fullName;
    private String email;
    private LocalDateTime friendsSince;
}
