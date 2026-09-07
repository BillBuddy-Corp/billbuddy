package com.billbuddy.backend.features.expenses.controller;

import com.billbuddy.backend.features.expenses.dto.request.CreateExpenseCommentRequest;
import com.billbuddy.backend.features.expenses.dto.response.ExpenseCommentResponse;
import com.billbuddy.backend.features.expenses.service.ExpenseCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/expenses/{expenseId}/comments")
@Tag(name = "Expense Comments", description = "Comment creation, listing, and deletion APIs")
@SecurityRequirement(name = "bearerAuth")
public class ExpenseCommentController {

    private final ExpenseCommentService expenseCommentService;

    public ExpenseCommentController(ExpenseCommentService expenseCommentService) {
        this.expenseCommentService = expenseCommentService;
    }

    @PostMapping
    @Operation(summary = "Add a comment", description = "Posts a comment on the expense; caller must be an active member of the expense's group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Comment created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "403", description = "Not a member of this group"),
            @ApiResponse(responseCode = "404", description = "Expense not found")
    })
    public ResponseEntity<ExpenseCommentResponse> createComment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long expenseId,
            @Valid @RequestBody CreateExpenseCommentRequest request
    ) {
        ExpenseCommentResponse response = expenseCommentService.createComment(expenseId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List an expense's comments", description = "Lists all comments on the expense, oldest first; caller must be an active member of the expense's group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Comments retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Not a member of this group"),
            @ApiResponse(responseCode = "404", description = "Expense not found")
    })
    public ResponseEntity<List<ExpenseCommentResponse>> listComments(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long expenseId
    ) {
        return ResponseEntity.ok(expenseCommentService.listComments(expenseId, userId));
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "Delete a comment", description = "Deletes a comment; only the comment's author or a group admin may delete")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Comment deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Not the comment's author or a group admin"),
            @ApiResponse(responseCode = "404", description = "Expense or comment not found")
    })
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long expenseId,
            @PathVariable Long commentId
    ) {
        expenseCommentService.deleteComment(expenseId, commentId, userId);
        return ResponseEntity.noContent().build();
    }
}
