package com.billbuddy.backend.features.billscanner.repository;

import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.billscanner.model.ReceiptScan;
import com.billbuddy.backend.features.storage.model.StoredFile;
import com.billbuddy.backend.features.storage.repository.StoredFileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReceiptScanRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Autowired
    private ReceiptScanRepository receiptScanRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistUser(String email) {
        return userRepository.save(User.signupWithEmail(email, "hashed-password", "User", null));
    }

    private StoredFile persistFile(User uploader, String storageKey) {
        return storedFileRepository.save(StoredFile.create(uploader, "image/jpeg", 1024L, storageKey));
    }

    @Test
    void findByFile_Id_returnsTheScan_whenOneExists() {
        User uploader = persistUser("jane@example.com");
        StoredFile file = persistFile(uploader, "receipt.jpg");
        ReceiptScan scan = receiptScanRepository.save(ReceiptScan.create(
                file, "Tesco", new BigDecimal("37.67"), "EUR", null,
                new BigDecimal("7.01"), null, new BigDecimal("41.85"), false, true
        ));
        entityManager.flush();
        entityManager.clear();

        Optional<ReceiptScan> result = receiptScanRepository.findByFile_Id(file.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(scan.getId());
        assertThat(result.get().getMerchant()).isEqualTo("Tesco");
        assertThat(result.get().getAmount()).isEqualByComparingTo("37.67");
        assertThat(result.get().isNeedsReview()).isFalse();
        assertThat(result.get().isDiscountsNeedReview()).isTrue();
    }

    @Test
    void findByFile_Id_returnsEmpty_whenNeverScanned() {
        User uploader = persistUser("jane@example.com");
        StoredFile file = persistFile(uploader, "unscanned.jpg");
        entityManager.flush();

        Optional<ReceiptScan> result = receiptScanRepository.findByFile_Id(file.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void fileId_mustBeUnique_onlyOneScanPerFile() {
        User uploader = persistUser("jane@example.com");
        StoredFile file = persistFile(uploader, "receipt.jpg");
        receiptScanRepository.save(ReceiptScan.create(
                file, "Tesco", new BigDecimal("37.67"), "EUR", null, null, null, null, false, false
        ));
        entityManager.flush();

        ReceiptScan duplicate = ReceiptScan.create(
                file, "Tesco Again", new BigDecimal("10.00"), "EUR", null, null, null, null, false, false
        );

        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> {
                    receiptScanRepository.save(duplicate);
                    entityManager.flush();
                }
        );
    }
}
