package com.billbuddy.backend.features.friends.controller;

import com.billbuddy.backend.features.expenses.dto.request.CreateExpenseRequest;
import com.billbuddy.backend.features.expenses.dto.response.ExpenseResponse;
import com.billbuddy.backend.features.expenses.service.ExpenseService;
import com.billbuddy.backend.features.friends.dto.response.FriendBalanceResponse;
import com.billbuddy.backend.features.settlements.service.BalanceService;
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
@RequestMapping("/api/v1/friends/{friendUserId}")
@Tag(name = "Friend Expenses", description = "Non-group (1:1) expense and balance APIs")
@SecurityRequirement(name = "bearerAuth")
public class FriendExpenseController {

    private final ExpenseService expenseService;
    private final BalanceService balanceService;

    public FriendExpenseController(ExpenseService expenseService, BalanceService balanceService) {
        this.expenseService = expenseService;
        this.balanceService = balanceService;
    }

    @PostMapping("/expenses")
    @Operation(summary = "Add a non-group expense", description = "Creates a 1:1 expense with a friend; both must already be friends, and only the two of them can be participants")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Expense created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input, split math doesn't reconcile, or a participant other than the two friends"),
            @ApiResponse(responseCode = "404", description = "Not friends with this user")
    })
    public ResponseEntity<ExpenseResponse> createFriendExpense(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long friendUserId,
            @Valid @RequestBody CreateExpenseRequest request
    ) {
        ExpenseResponse response = expenseService.createFriendExpense(userId, friendUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/expenses")
    @Operation(summary = "List non-group expenses with a friend", description = "Lists all active 1:1 expenses between the caller and this friend")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Expenses retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Not friends with this user")
    })
    public ResponseEntity<List<ExpenseResponse>> listFriendExpenses(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long friendUserId
    ) {
        return ResponseEntity.ok(expenseService.listFriendExpenses(userId, friendUserId));
    }

    @GetMapping("/balance")
    @Operation(summary = "Get combined balance with a friend", description = "Net balance per currency, combining every shared group plus non-group expenses; positive means the friend owes the caller")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Balance retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Not friends with this user")
    })
    public ResponseEntity<List<FriendBalanceResponse>> getFriendBalance(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long friendUserId
    ) {
        return ResponseEntity.ok(balanceService.getFriendBalance(userId, friendUserId));
    }
}
