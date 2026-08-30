package com.billbuddy.backend.features.groups.model;

import com.billbuddy.backend.features.auth.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "groups")
public class Group {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(name = "default_currency", nullable = false)
    private String defaultCurrency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private Group(
            String name,
            String description,
            String defaultCurrency,
            User createdBy
    ) {
        this.name = name;
        this.description = description;
        this.defaultCurrency = defaultCurrency;
        this.createdBy = createdBy;
    }

    public static Group create(
            String name,
            String description,
            String defaultCurrency,
            User createdBy
    ) {
        return Group.builder()
                .name(name)
                .description(description)
                .defaultCurrency(defaultCurrency)
                .createdBy(createdBy)
                .build();
    }

    public void update(String name, String description, String defaultCurrency) {
        this.name = name;
        this.description = description;
        this.defaultCurrency = defaultCurrency;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
