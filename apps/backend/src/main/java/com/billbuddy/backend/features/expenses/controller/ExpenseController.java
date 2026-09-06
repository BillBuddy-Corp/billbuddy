package com.billbuddy.backend.features.expenses.controller;

import com.billbuddy.backend.features.expenses.dto.request.CreateExpenseRequest;
import com.billbuddy.backend.features.expenses.dto.response.ExchangeRateResponse;
import com.billbuddy.backend.features.expenses.dto.response.ExpenseResponse;
import com.billbuddy.backend.features.expenses.service.ExpenseService;
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
@RequestMapping("/api/v1/groups/{groupId}/expenses")
@Tag(name = "Expenses", description = "Expense creation and listing APIs")
@SecurityRequirement(name = "bearerAuth")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    @Operation(summary = "Add an expense", description = "Creates an expense with any split type (EQUAL, PERCENTAGE, EXACT, ITEMIZED)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Expense created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input, split math doesn't reconcile, or currency mismatch"),
            @ApiResponse(responseCode = "403", description = "Not a member of this group"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public ResponseEntity<ExpenseResponse> createExpense(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long groupId,
            @Valid @RequestBody CreateExpenseRequest request
    ) {
        ExpenseResponse response = expenseService.createExpense(groupId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List a group's expenses", description = "Lists all active expenses for the group; caller must be a member")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Expenses retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Not a member of this group"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public ResponseEntity<List<ExpenseResponse>> listExpenses(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long groupId
    ) {
        return ResponseEntity.ok(expenseService.listExpenses(groupId, userId));
    }

    @GetMapping("/exchange-rate")
    @Operation(
            summary = "Suggest an exchange rate",
            description = "Suggests a live exchange rate from fromCurrency to the group's default currency, for the client to prefill and let the user confirm or override before creating an expense"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Exchange rate suggested successfully"),
            @ApiResponse(responseCode = "400", description = "fromCurrency isn't a real currency code, or no rate could be found"),
            @ApiResponse(responseCode = "403", description = "Not a member of this group"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public ResponseEntity<ExchangeRateResponse> suggestExchangeRate(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long groupId,
            @RequestParam String fromCurrency
    ) {
        return ResponseEntity.ok(expenseService.suggestExchangeRate(groupId, userId, fromCurrency));
    }
}
