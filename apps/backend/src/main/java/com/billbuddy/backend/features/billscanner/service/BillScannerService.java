package com.billbuddy.backend.features.billscanner.service;

import com.billbuddy.backend.exception.ReceiptScanFailedException;
import com.billbuddy.backend.features.billscanner.dto.response.ReceiptScanItemResponse;
import com.billbuddy.backend.features.billscanner.dto.response.ReceiptScanResponse;
import com.billbuddy.backend.features.billscanner.model.ReceiptScan;
import com.billbuddy.backend.features.billscanner.model.ReceiptScanItem;
import com.billbuddy.backend.features.billscanner.repository.ReceiptScanItemRepository;
import com.billbuddy.backend.features.billscanner.repository.ReceiptScanRepository;
import com.billbuddy.backend.features.storage.model.StoredFile;
import com.billbuddy.backend.features.storage.service.FileService;
import com.billbuddy.backend.features.storage.service.FileStorageService;
import jakarta.transaction.Transactional;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class BillScannerService {

    private final FileService fileService;
    private final FileStorageService fileStorageService;
    private final ReceiptScannerService receiptScannerService;
    private final ReceiptScanRepository receiptScanRepository;
    private final ReceiptScanItemRepository receiptScanItemRepository;

    public BillScannerService(
            FileService fileService,
            FileStorageService fileStorageService,
            ReceiptScannerService receiptScannerService,
            ReceiptScanRepository receiptScanRepository,
            ReceiptScanItemRepository receiptScanItemRepository
    ) {
        this.fileService = fileService;
        this.fileStorageService = fileStorageService;
        this.receiptScannerService = receiptScannerService;
        this.receiptScanRepository = receiptScanRepository;
        this.receiptScanItemRepository = receiptScanItemRepository;
    }

    @Transactional
    public ReceiptScanResponse scanReceipt(Long fileId, Long requesterId) {
        StoredFile file = fileService.requireOwnedFile(fileId, requesterId);

        Optional<ReceiptScan> cached = receiptScanRepository.findByFile_Id(fileId);
        if (cached.isPresent()) {
            return toResponse(cached.get());
        }

        byte[] imageBytes = readBytes(file);
        ReceiptScannerService.ScannedReceipt scanned = receiptScannerService.scan(imageBytes, file.getContentType());
        List<ReceiptScannerService.ScannedLineItem> normalizedItems = ReceiptItemNormalizer.normalize(scanned.items());
        BigDecimal subtotal = normalizedItems.stream()
                .map(ReceiptScannerService.ScannedLineItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        ReceiptDiscountProrator.ReconciliationResult reconciliation =
                ReceiptDiscountProrator.reconcileToTotal(normalizedItems, scanned.amount());
        boolean discountsNeedReview = hasSubtotalMismatch(subtotal, scanned.otherDiscount(), scanned.amount());

        ReceiptScan receiptScan = ReceiptScan.create(
                file, scanned.merchant(), scanned.amount(), scanned.currency(), scanned.transactionDate(),
                scanned.otherDiscount(), scanned.voucherAmount(), subtotal,
                reconciliation.needsReview(), discountsNeedReview
        );
        final ReceiptScan savedScan = receiptScanRepository.save(receiptScan);

        List<ReceiptScanItem> items = reconciliation.items().stream()
                .map(i -> ReceiptScanItem.create(savedScan, i.name(), i.amount(), i.quantity()))
                .toList();
        receiptScanItemRepository.saveAll(items);

        return toResponse(savedScan, items);
    }

    // ===================== HELPERS =====================

    // Item-level reconciliation always forces sum(items) to equal amount by construction, so it
    // can't catch otherDiscount/subtotal being wrong on its own -- this checks that the numbers
    // Claude reported are actually consistent with each other, beyond simple rounding. Kept
    // separate from needsReview: it flags the informational discount breakdown, not whether the
    // item prices (what actually matters for splitting the bill) can be trusted.
    private static boolean hasSubtotalMismatch(BigDecimal subtotal, BigDecimal otherDiscount, BigDecimal amount) {
        if (amount == null) {
            return false; // reconciliation already flags a missing total
        }
        BigDecimal discount = otherDiscount != null ? otherDiscount : BigDecimal.ZERO;
        BigDecimal expectedAmount = subtotal.subtract(discount);
        BigDecimal gap = expectedAmount.subtract(amount).abs();
        return gap.compareTo(new BigDecimal("0.02")) > 0;
    }

    private byte[] readBytes(StoredFile file) {
        Resource resource = fileStorageService.load(file.getStorageKey());
        try {
            return resource.getInputStream().readAllBytes();
        } catch (IOException ex) {
            throw new ReceiptScanFailedException("Could not read the uploaded file");
        }
    }

    private ReceiptScanResponse toResponse(ReceiptScan receiptScan) {
        List<ReceiptScanItem> items = receiptScanItemRepository.findByReceiptScan_Id(receiptScan.getId());
        return toResponse(receiptScan, items);
    }

    private ReceiptScanResponse toResponse(ReceiptScan receiptScan, List<ReceiptScanItem> items) {
        List<ReceiptScanItemResponse> itemResponses = items.stream()
                .map(i -> new ReceiptScanItemResponse(i.getName(), i.getAmount(), i.getQuantity()))
                .toList();

        return new ReceiptScanResponse(
                receiptScan.getFile().getId(),
                receiptScan.getMerchant(),
                receiptScan.getAmount(),
                receiptScan.getCurrency(),
                receiptScan.getTransactionDate(),
                receiptScan.getOtherDiscount(),
                receiptScan.getVoucherAmount(),
                receiptScan.getSubtotal(),
                receiptScan.isNeedsReview(),
                receiptScan.isDiscountsNeedReview(),
                itemResponses
        );
    }
}
