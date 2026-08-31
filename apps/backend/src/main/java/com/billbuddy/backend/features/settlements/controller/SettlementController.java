package com.billbuddy.backend.features.settlements.controller;

import com.billbuddy.backend.features.settlements.dto.request.CreateSettlementRequest;
import com.billbuddy.backend.features.settlements.dto.response.SettlementResponse;
import com.billbuddy.backend.features.settlements.service.SettlementService;
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
@RequestMapping("/api/v1/groups/{groupId}/settlements")
@Tag(name = "Settlements", description = "Settlement creation and listing APIs")
@SecurityRequirement(name = "bearerAuth")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping
    @Operation(summary = "Log a settlement", description = "Records a repayment between two active group members")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Settlement created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input, same payer/recipient, or currency mismatch"),
            @ApiResponse(responseCode = "403", description = "Not a member of this group"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public ResponseEntity<SettlementResponse> createSettlement(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long groupId,
            @Valid @RequestBody CreateSettlementRequest request
    ) {
        SettlementResponse response = settlementService.createSettlement(groupId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List a group's settlements", description = "Lists all active settlements for the group; caller must be a member")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Settlements retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Not a member of this group"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public ResponseEntity<List<SettlementResponse>> listSettlements(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long groupId
    ) {
        return ResponseEntity.ok(settlementService.listSettlements(groupId, userId));
    }
}
