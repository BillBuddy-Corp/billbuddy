package com.billbuddy.backend.features.billscanner.controller;

import com.billbuddy.backend.features.billscanner.dto.response.ReceiptScanResponse;
import com.billbuddy.backend.features.billscanner.service.BillScannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/files/{fileId}/scan-receipt")
@Tag(name = "Bill Scanner", description = "Receipt extraction APIs")
@SecurityRequirement(name = "bearerAuth")
public class BillScannerController {

    private final BillScannerService billScannerService;

    public BillScannerController(BillScannerService billScannerService) {
        this.billScannerService = billScannerService;
    }

    @PostMapping
    @Operation(
            summary = "Scan a receipt",
            description = "Extracts merchant, total, currency, date, and line items from an already-uploaded receipt image. Cached after the first successful scan -- repeat calls return the same result without re-scanning"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Receipt scanned (or cached result returned)"),
            @ApiResponse(responseCode = "400", description = "The image doesn't look like a readable receipt"),
            @ApiResponse(responseCode = "403", description = "You didn't upload this file"),
            @ApiResponse(responseCode = "404", description = "File not found")
    })
    public ResponseEntity<ReceiptScanResponse> scanReceipt(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long fileId
    ) {
        return ResponseEntity.ok(billScannerService.scanReceipt(fileId, userId));
    }
}
