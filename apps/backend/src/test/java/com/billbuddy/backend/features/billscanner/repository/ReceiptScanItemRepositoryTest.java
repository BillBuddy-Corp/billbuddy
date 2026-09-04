package com.billbuddy.backend.features.billscanner.repository;

import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.billscanner.model.ReceiptScan;
import com.billbuddy.backend.features.billscanner.model.ReceiptScanItem;
import com.billbuddy.backend.features.storage.model.StoredFile;
import com.billbuddy.backend.features.storage.repository.StoredFileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReceiptScanItemRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Autowired
    private ReceiptScanRepository receiptScanRepository;

    @Autowired
    private ReceiptScanItemRepository receiptScanItemRepository;

    @Autowired
    private TestEntityManager entityManager;

    private ReceiptScan persistScan() {
        User uploader = userRepository.save(User.signupWithEmail("jane@example.com", "hashed-password", "Jane", null));
        StoredFile file = storedFileRepository.save(StoredFile.create(uploader, "image/jpeg", 1024L, "receipt.jpg"));
        return receiptScanRepository.save(ReceiptScan.create(
                file, "Tesco", new BigDecimal("37.67"), "EUR", null, null, null, null, false, false
        ));
    }

    @Test
    void findByReceiptScan_Id_returnsAllItems_forThatScan() {
        ReceiptScan scan = persistScan();
        receiptScanItemRepository.save(ReceiptScanItem.create(scan, "Bread", new BigDecimal("2.10"), 1));
        receiptScanItemRepository.save(ReceiptScanItem.create(scan, "Milk", new BigDecimal("1.85"), 1));
        entityManager.flush();
        entityManager.clear();

        List<ReceiptScanItem> items = receiptScanItemRepository.findByReceiptScan_Id(scan.getId());

        assertThat(items).hasSize(2);
        assertThat(items).extracting(ReceiptScanItem::getName).containsExactlyInAnyOrder("Bread", "Milk");
    }

    @Test
    void findByReceiptScan_Id_returnsEmpty_whenScanHasNoItems() {
        ReceiptScan scan = persistScan();
        entityManager.flush();

        List<ReceiptScanItem> items = receiptScanItemRepository.findByReceiptScan_Id(scan.getId());

        assertThat(items).isEmpty();
    }

    @Test
    void findByReceiptScan_Id_doesNotReturnItemsFromOtherScans() {
        ReceiptScan scanA = persistScan();
        User otherUploader = userRepository.save(User.signupWithEmail("bob@example.com", "hashed-password", "Bob", null));
        StoredFile otherFile = storedFileRepository.save(StoredFile.create(otherUploader, "image/jpeg", 1024L, "other.jpg"));
        ReceiptScan scanB = receiptScanRepository.save(ReceiptScan.create(
                otherFile, "Aldi", new BigDecimal("10.00"), "EUR", null, null, null, null, false, false
        ));
        receiptScanItemRepository.save(ReceiptScanItem.create(scanA, "Bread", new BigDecimal("2.10"), 1));
        receiptScanItemRepository.save(ReceiptScanItem.create(scanB, "Eggs", new BigDecimal("1.39"), 1));
        entityManager.flush();
        entityManager.clear();

        List<ReceiptScanItem> items = receiptScanItemRepository.findByReceiptScan_Id(scanA.getId());

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getName()).isEqualTo("Bread");
    }
}
