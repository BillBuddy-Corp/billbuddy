package com.billbuddy.backend.features.storage.model;

import com.billbuddy.backend.features.auth.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "files")
public class StoredFile {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private StoredFile(User uploadedBy, String contentType, Long fileSizeBytes, String storageKey) {
        this.uploadedBy = uploadedBy;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.storageKey = storageKey;
    }

    public static StoredFile create(User uploadedBy, String contentType, Long fileSizeBytes, String storageKey) {
        return StoredFile.builder()
                .uploadedBy(uploadedBy)
                .contentType(contentType)
                .fileSizeBytes(fileSizeBytes)
                .storageKey(storageKey)
                .build();
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
