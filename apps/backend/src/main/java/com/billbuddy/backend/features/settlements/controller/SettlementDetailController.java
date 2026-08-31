package com.billbuddy.backend.features.settlements.controller;

import com.billbuddy.backend.features.settlements.dto.request.UpdateSettlementRequest;
import com.billbuddy.backend.features.settlements.dto.response.SettlementResponse;
import com.billbuddy.backend.features.settlements.service.SettlementService;
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
@RequestMapping("/api/v1/settlements")
@Tag(name = "Settlements", description = "Settlement detail, edit, and delete APIs")
@SecurityRequirement(name = "bearerAuth")
public class SettlementDetailController {

    private final SettlementService settlementService;

    public SettlementDetailController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping("/{settlementId}")
    @Operation(summary = "Get settlement details", description = "Returns full settlement detail")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Settlement retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Not a member of this group"),
            @ApiResponse(responseCode = "404", description = "Settlement not found")
    })
    public ResponseEntity<SettlementResponse> getSettlement(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long settlementId
    ) {
        return ResponseEntity.ok(settlementService.getSettlement(settlementId, userId));
    }

    @PutMapping("/{settlementId}")
    @Operation(summary = "Edit a settlement", description = "Full replace of a settlement; only the logger or a group admin can edit")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Settlement updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input, same payer/recipient, or currency mismatch"),
            @ApiResponse(responseCode = "403", description = "Not the logger or a group admin"),
            @ApiResponse(responseCode = "404", description = "Settlement not found")
    })
    public ResponseEntity<SettlementResponse> updateSettlement(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long settlementId,
            @Valid @RequestBody UpdateSettlementRequest request
    ) {
        return ResponseEntity.ok(settlementService.updateSettlement(settlementId, userId, request));
    }

    @DeleteMapping("/{settlementId}")
    @Operation(summary = "Revoke a settlement", description = "Soft-deletes a settlement; only the logger or a group admin can revoke")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Settlement revoked successfully"),
            @ApiResponse(responseCode = "403", description = "Not the logger or a group admin"),
            @ApiResponse(responseCode = "404", description = "Settlement not found")
    })
    public ResponseEntity<Void> deleteSettlement(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long settlementId
    ) {
        settlementService.deleteSettlement(settlementId, userId);
        return ResponseEntity.noContent().build();
    }
}
