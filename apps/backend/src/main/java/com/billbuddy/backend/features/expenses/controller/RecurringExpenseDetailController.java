package com.billbuddy.backend.features.expenses.controller;

import com.billbuddy.backend.features.expenses.service.RecurringExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/recurring-expenses")
@Tag(name = "Recurring Expenses", description = "Recurring expense pause/resume/cancel APIs")
@SecurityRequirement(name = "bearerAuth")
public class RecurringExpenseDetailController {

    private final RecurringExpenseService recurringExpenseService;

    public RecurringExpenseDetailController(RecurringExpenseService recurringExpenseService) {
        this.recurringExpenseService = recurringExpenseService;
    }

    @PostMapping("/{templateId}/pause")
    @Operation(summary = "Pause a recurring expense", description = "Stops future auto-generation until resumed; only the creator or a group admin can pause")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recurring expense paused successfully"),
            @ApiResponse(responseCode = "403", description = "Not the creator or a group admin"),
            @ApiResponse(responseCode = "404", description = "Recurring expense not found")
    })
    public ResponseEntity<Void> pauseTemplate(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long templateId
    ) {
        recurringExpenseService.pauseTemplate(templateId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{templateId}/resume")
    @Operation(summary = "Resume a recurring expense", description = "Resumes future auto-generation; only the creator or a group admin can resume")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recurring expense resumed successfully"),
            @ApiResponse(responseCode = "403", description = "Not the creator or a group admin"),
            @ApiResponse(responseCode = "404", description = "Recurring expense not found")
    })
    public ResponseEntity<Void> resumeTemplate(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long templateId
    ) {
        recurringExpenseService.resumeTemplate(templateId, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{templateId}")
    @Operation(summary = "Cancel a recurring expense", description = "Soft-deletes the template, stopping all future generation; already-generated expenses are untouched. Only the creator or a group admin can cancel")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Recurring expense cancelled successfully"),
            @ApiResponse(responseCode = "403", description = "Not the creator or a group admin"),
            @ApiResponse(responseCode = "404", description = "Recurring expense not found")
    })
    public ResponseEntity<Void> cancelTemplate(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long templateId
    ) {
        recurringExpenseService.cancelTemplate(templateId, userId);
        return ResponseEntity.noContent().build();
    }
}
