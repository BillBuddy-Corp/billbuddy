package com.billbuddy.backend.features.expenses.controller;

import com.billbuddy.backend.features.expenses.dto.request.UpdateExpenseRequest;
import com.billbuddy.backend.features.expenses.dto.response.ExpenseResponse;
import com.billbuddy.backend.features.expenses.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/expenses")
@Tag(name = "Expenses", description = "Expense detail, edit, and delete APIs")
@SecurityRequirement(name = "bearerAuth")
public class ExpenseDetailController {

    private final ExpenseService expenseService;

    public ExpenseDetailController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping("/{expenseId}")
    @Operation(summary = "Get expense details", description = "Returns full expense detail including splits and, for ITEMIZED expenses, items")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Expense retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Not a member of this group"),
            @ApiResponse(responseCode = "404", description = "Expense not found")
    })
    public ResponseEntity<ExpenseResponse> getExpense(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long expenseId
    ) {
        return ResponseEntity.ok(expenseService.getExpense(expenseId, userId));
    }

    @PutMapping("/{expenseId}")
    @Operation(summary = "Edit an expense", description = "Full replace of an expense's split; only the creator or a group admin can edit")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Expense updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input, split math doesn't reconcile, or currency mismatch"),
            @ApiResponse(responseCode = "403", description = "Not the creator or a group admin"),
            @ApiResponse(responseCode = "404", description = "Expense not found")
    })
    public ResponseEntity<ExpenseResponse> updateExpense(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long expenseId,
            @Valid @RequestBody UpdateExpenseRequest request
    ) {
        return ResponseEntity.ok(expenseService.updateExpense(expenseId, userId, request));
    }

    @DeleteMapping("/{expenseId}")
    @Operation(summary = "Delete an expense", description = "Soft-deletes an expense; only the creator or a group admin can delete")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Expense deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Not the creator or a group admin"),
            @ApiResponse(responseCode = "404", description = "Expense not found")
    })
    public ResponseEntity<Void> deleteExpense(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long expenseId
    ) {
        expenseService.deleteExpense(expenseId, userId);
        return ResponseEntity.noContent().build();
    }
}
