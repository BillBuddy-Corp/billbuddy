package com.billbuddy.backend.features.settlements.controller;

import com.billbuddy.backend.features.settlements.dto.response.BalanceResponse;
import com.billbuddy.backend.features.settlements.dto.response.SimplifiedSettlementResponse;
import com.billbuddy.backend.features.settlements.service.BalanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/balances")
@Tag(name = "Balances", description = "Computed per-group balance APIs")
@SecurityRequirement(name = "bearerAuth")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping
    @Operation(summary = "Get raw balances", description = "Net balance per active member, computed from expenses and settlements; positive means the group owes them")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Balances retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Not a member of this group"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public ResponseEntity<List<BalanceResponse>> getBalances(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long groupId
    ) {
        return ResponseEntity.ok(balanceService.getBalances(groupId, userId));
    }

    @GetMapping("/simplified")
    @Operation(summary = "Get simplified balances", description = "Minimum set of payments that would settle the whole group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Simplified balances retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Not a member of this group"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public ResponseEntity<List<SimplifiedSettlementResponse>> getSimplifiedBalances(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long groupId
    ) {
        return ResponseEntity.ok(balanceService.getSimplifiedBalances(groupId, userId));
    }
}
