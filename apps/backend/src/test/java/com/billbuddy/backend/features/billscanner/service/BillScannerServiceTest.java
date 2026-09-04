package com.billbuddy.backend.features.billscanner.service;

import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.billscanner.dto.response.ReceiptScanResponse;
import com.billbuddy.backend.features.billscanner.model.ReceiptScan;
import com.billbuddy.backend.features.billscanner.model.ReceiptScanItem;
import com.billbuddy.backend.features.billscanner.repository.ReceiptScanItemRepository;
import com.billbuddy.backend.features.billscanner.repository.ReceiptScanRepository;
import com.billbuddy.backend.features.storage.model.StoredFile;
import com.billbuddy.backend.features.storage.service.FileService;
import com.billbuddy.backend.features.storage.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillScannerServiceTest {

    @Mock
    private FileService fileService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ReceiptScannerService receiptScannerService;

    @Mock
    private ReceiptScanRepository receiptScanRepository;

    @Mock
    private ReceiptScanItemRepository receiptScanItemRepository;

    private BillScannerService billScannerService;

    private void init() {
        billScannerService = new BillScannerService(
                fileService, fileStorageService, receiptScannerService, receiptScanRepository, receiptScanItemRepository
        );
    }

    private StoredFile buildFile(Long id, String contentType) {
        User uploader = User.signupWithEmail("jane@example.com", "hashed-password", "Jane", null);
        ReflectionTestUtils.setField(uploader, "id", 1L);
        StoredFile file = StoredFile.create(uploader, contentType, 1024L, "key-" + id + ".jpg");
        ReflectionTestUtils.setField(file, "id", id);
        return file;
    }

    private ReceiptScannerService.ScannedLineItem item(String name, String amount, BigDecimal itemDiscount) {
        return new ReceiptScannerService.ScannedLineItem(name, new BigDecimal(amount), 1, itemDiscount);
    }

    private void stubSave() {
        when(receiptScanRepository.save(any(ReceiptScan.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ===================== CACHING =====================

    @Test
    void scanReceipt_returnsCachedResult_whenAlreadyScanned() {
        init();
        StoredFile file = buildFile(100L, "image/jpeg");
        when(fileService.requireOwnedFile(100L, 1L)).thenReturn(file);

        ReceiptScan cached = ReceiptScan.create(
                file, "Tesco", new BigDecimal("37.67"), "EUR", null,
                new BigDecimal("7.01"), null, new BigDecimal("41.85"), false, true
        );
        ReflectionTestUtils.setField(cached, "id", 5L);
        when(receiptScanRepository.findByFile_Id(100L)).thenReturn(Optional.of(cached));
        when(receiptScanItemRepository.findByReceiptScan_Id(5L)).thenReturn(List.of());

        ReceiptScanResponse response = billScannerService.scanReceipt(100L, 1L);

        assertThat(response.getMerchant()).isEqualTo("Tesco");
        assertThat(response.getAmount()).isEqualByComparingTo("37.67");
        verifyNoInteractions(receiptScannerService);
        verifyNoInteractions(fileStorageService);
    }

    // ===================== FRESH SCAN =====================

    @Test
    void scanReceipt_scansAndPersists_whenNotCached() {
        init();
        StoredFile file = buildFile(100L, "image/jpeg");
        when(fileService.requireOwnedFile(100L, 1L)).thenReturn(file);
        when(receiptScanRepository.findByFile_Id(100L)).thenReturn(Optional.empty());
        when(fileStorageService.load(file.getStorageKey())).thenReturn(new ByteArrayResource("bytes".getBytes()));
        stubSave();

        ReceiptScannerService.ScannedReceipt scanned = new ReceiptScannerService.ScannedReceipt(
                "Tesco Ireland", new BigDecimal("37.67"), "EUR", LocalDate.of(2026, 3, 15),
                new BigDecimal("7.01"), null,
                List.of(item("Bread", "40.00", null), item("Milk", "60.00", null))
        );
        when(receiptScannerService.scan(any(), eq("image/jpeg"))).thenReturn(scanned);

        ReceiptScanResponse response = billScannerService.scanReceipt(100L, 1L);

        assertThat(response.getMerchant()).isEqualTo("Tesco Ireland");
        assertThat(response.getAmount()).isEqualByComparingTo("37.67");
        assertThat(response.getTransactionDate()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(response.getItems()).hasSize(2);
        BigDecimal itemsSum = response.getItems().stream()
                .map(com.billbuddy.backend.features.billscanner.dto.response.ReceiptScanItemResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(itemsSum).isEqualByComparingTo(response.getAmount());
        verify(receiptScanItemRepository).saveAll(any());
    }

    @Test
    void scanReceipt_computesSubtotal_asRawNormalizedSum_beforeProration() {
        init();
        StoredFile file = buildFile(100L, "image/jpeg");
        when(fileService.requireOwnedFile(100L, 1L)).thenReturn(file);
        when(receiptScanRepository.findByFile_Id(100L)).thenReturn(Optional.empty());
        when(fileStorageService.load(file.getStorageKey())).thenReturn(new ByteArrayResource("bytes".getBytes()));
        stubSave();

        // items net to 41.85 before the whole-receipt discount is proportionally applied to reach 37.67
        ReceiptScannerService.ScannedReceipt scanned = new ReceiptScannerService.ScannedReceipt(
                "Tesco", new BigDecimal("37.67"), "EUR", null, new BigDecimal("4.18"), null,
                List.of(item("A", "20.925", null), item("B", "20.925", null))
        );
        when(receiptScannerService.scan(any(), any())).thenReturn(scanned);

        ReceiptScanResponse response = billScannerService.scanReceipt(100L, 1L);

        assertThat(response.getSubtotal()).isEqualByComparingTo("41.85");
        assertThat(response.getAmount()).isEqualByComparingTo("37.67");
    }

    // ===================== NEEDS REVIEW =====================

    @Test
    void scanReceipt_needsReviewFalse_whenItemsReconcileToTotal() {
        init();
        StoredFile file = buildFile(100L, "image/jpeg");
        when(fileService.requireOwnedFile(100L, 1L)).thenReturn(file);
        when(receiptScanRepository.findByFile_Id(100L)).thenReturn(Optional.empty());
        when(fileStorageService.load(file.getStorageKey())).thenReturn(new ByteArrayResource("bytes".getBytes()));
        stubSave();

        ReceiptScannerService.ScannedReceipt scanned = new ReceiptScannerService.ScannedReceipt(
                "Shop", new BigDecimal("10.00"), "EUR", null, null, null,
                List.of(item("Item", "10.00", null))
        );
        when(receiptScannerService.scan(any(), any())).thenReturn(scanned);

        ReceiptScanResponse response = billScannerService.scanReceipt(100L, 1L);

        assertThat(response.isNeedsReview()).isFalse();
    }

    @Test
    void scanReceipt_needsReviewTrue_whenNoItemsWereExtracted() {
        init();
        StoredFile file = buildFile(100L, "image/jpeg");
        when(fileService.requireOwnedFile(100L, 1L)).thenReturn(file);
        when(receiptScanRepository.findByFile_Id(100L)).thenReturn(Optional.empty());
        when(fileStorageService.load(file.getStorageKey())).thenReturn(new ByteArrayResource("bytes".getBytes()));
        stubSave();

        ReceiptScannerService.ScannedReceipt scanned = new ReceiptScannerService.ScannedReceipt(
                "Shop", new BigDecimal("10.00"), "EUR", null, null, null, List.of()
        );
        when(receiptScannerService.scan(any(), any())).thenReturn(scanned);

        ReceiptScanResponse response = billScannerService.scanReceipt(100L, 1L);

        assertThat(response.isNeedsReview()).isTrue();
    }

    // ===================== DISCOUNTS NEED REVIEW =====================

    @Test
    void scanReceipt_discountsNeedReviewFalse_whenSubtotalMinusDiscountMatchesAmount() {
        init();
        StoredFile file = buildFile(100L, "image/jpeg");
        when(fileService.requireOwnedFile(100L, 1L)).thenReturn(file);
        when(receiptScanRepository.findByFile_Id(100L)).thenReturn(Optional.empty());
        when(fileStorageService.load(file.getStorageKey())).thenReturn(new ByteArrayResource("bytes".getBytes()));
        stubSave();

        // subtotal 8.00 - otherDiscount 2.00 = amount 6.00 -- fully consistent, and 6/8 is a clean
        // ratio so reconciliation succeeds without hitting its own rounding-dust bailout
        ReceiptScannerService.ScannedReceipt scanned = new ReceiptScannerService.ScannedReceipt(
                "Shop", new BigDecimal("6.00"), "EUR", null, new BigDecimal("2.00"), null,
                List.of(item("Item", "8.00", null))
        );
        when(receiptScannerService.scan(any(), any())).thenReturn(scanned);

        ReceiptScanResponse response = billScannerService.scanReceipt(100L, 1L);

        assertThat(response.isDiscountsNeedReview()).isFalse();
        assertThat(response.isNeedsReview()).isFalse(); // item math is still fine either way
    }

    @Test
    void scanReceipt_discountsNeedReviewTrue_whenSubtotalMinusDiscountDoesNotMatchAmount_butItemsStillReconcile() {
        init();
        StoredFile file = buildFile(100L, "image/jpeg");
        when(fileService.requireOwnedFile(100L, 1L)).thenReturn(file);
        when(receiptScanRepository.findByFile_Id(100L)).thenReturn(Optional.empty());
        when(fileStorageService.load(file.getStorageKey())).thenReturn(new ByteArrayResource("bytes".getBytes()));
        stubSave();

        // subtotal 12.00 - otherDiscount 2.00 = 10.00, but the receipt's real total is 9.00 --
        // a genuine inconsistency in the discount bookkeeping, even though proration will still
        // force the item prices to sum to 9.00 regardless
        ReceiptScannerService.ScannedReceipt scanned = new ReceiptScannerService.ScannedReceipt(
                "Shop", new BigDecimal("9.00"), "EUR", null, new BigDecimal("2.00"), null,
                List.of(item("Item", "12.00", null))
        );
        when(receiptScannerService.scan(any(), any())).thenReturn(scanned);

        ReceiptScanResponse response = billScannerService.scanReceipt(100L, 1L);

        assertThat(response.isDiscountsNeedReview()).isTrue();
        assertThat(response.isNeedsReview()).isFalse();
        BigDecimal itemsSum = response.getItems().stream()
                .map(com.billbuddy.backend.features.billscanner.dto.response.ReceiptScanItemResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(itemsSum).isEqualByComparingTo("9.00");
    }
}
