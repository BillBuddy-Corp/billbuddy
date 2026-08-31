package com.billbuddy.backend.features.storage.repository;

import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.storage.model.StoredFile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StoredFileRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistUser(String email) {
        return userRepository.save(User.signupWithEmail(email, "hashed-password", "User", null));
    }

    @Test
    void findByIdAndDeletedAtIsNull_returnsTheFile_whenActive() {
        User uploader = persistUser("jane@example.com");
        StoredFile file = storedFileRepository.save(StoredFile.create(uploader, "image/png", 1024L, "key.png"));
        entityManager.flush();
        entityManager.clear();

        Optional<StoredFile> result = storedFileRepository.findByIdAndDeletedAtIsNull(file.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getContentType()).isEqualTo("image/png");
    }

    @Test
    void findByIdAndDeletedAtIsNull_excludesSoftDeletedFile() {
        User uploader = persistUser("jane@example.com");
        StoredFile file = storedFileRepository.save(StoredFile.create(uploader, "image/png", 1024L, "key.png"));
        file.softDelete();
        storedFileRepository.save(file);
        entityManager.flush();
        entityManager.clear();

        Optional<StoredFile> result = storedFileRepository.findByIdAndDeletedAtIsNull(file.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void storageKey_mustBeUnique() {
        User uploader = persistUser("jane@example.com");
        storedFileRepository.save(StoredFile.create(uploader, "image/png", 1024L, "duplicate-key.png"));
        entityManager.flush();

        StoredFile duplicate = StoredFile.create(uploader, "image/png", 2048L, "duplicate-key.png");

        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> {
                    storedFileRepository.save(duplicate);
                    entityManager.flush();
                }
        );
    }
}
