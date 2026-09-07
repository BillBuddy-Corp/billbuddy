package com.billbuddy.backend.features.expenses.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ExpenseCommentResponse {

    private Long id;
    private Long expenseId;
    private String body;
    private Long authorUserId;
    private String authorName;
    private LocalDateTime createdAt;
}
