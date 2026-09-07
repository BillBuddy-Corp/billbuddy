package com.billbuddy.backend.features.expenses.controller;

import com.billbuddy.backend.features.expenses.dto.request.CreateRecurringExpenseRequest;
import com.billbuddy.backend.features.expenses.dto.response.RecurringExpenseResponse;
import com.billbuddy.backend.features.expenses.service.RecurringExpenseService;
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
@RequestMapping("/api/v1/groups/{groupId}/recurring-expenses")
@Tag(name = "Recurring Expenses", description = "Recurring expense template creation and listing APIs")
@SecurityRequirement(name = "bearerAuth")
public class RecurringExpenseController {

    private final RecurringExpenseService recurringExpenseService;

    public RecurringExpenseController(RecurringExpenseService recurringExpenseService) {
        this.recurringExpenseService = recurringExpenseService;
    }

    @PostMapping
    @Operation(summary = "Create a recurring expense", description = "Creates a template that auto-generates a real expense on each due date (weekly or monthly)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Recurring expense created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input, split math doesn't reconcile, invalid currency, or invalid recurrence fields"),
            @ApiResponse(responseCode = "403", description = "Not a member of this group"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public ResponseEntity<RecurringExpenseResponse> createTemplate(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long groupId,
            @Valid @RequestBody CreateRecurringExpenseRequest request
    ) {
        RecurringExpenseResponse response = recurringExpenseService.createTemplate(groupId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List a group's recurring expenses", description = "Lists all active and paused (non-cancelled) recurring expense templates for the group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recurring expenses retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Not a member of this group"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public ResponseEntity<List<RecurringExpenseResponse>> listTemplates(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long groupId
    ) {
        return ResponseEntity.ok(recurringExpenseService.listTemplates(groupId, userId));
    }
}
