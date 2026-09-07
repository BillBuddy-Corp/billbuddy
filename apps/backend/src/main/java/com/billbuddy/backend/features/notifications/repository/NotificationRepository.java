package com.billbuddy.backend.features.notifications.repository;

import com.billbuddy.backend.features.notifications.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<Notification> findByIdAndUser_Id(Long id, Long userId);

    long countByUser_IdAndReadAtIsNull(Long userId);

    @Modifying
    @Query("""
        update Notification n
        set n.readAt = CURRENT_TIMESTAMP
        where n.user.id = :userId and n.readAt is null
    """)
    int markAllAsRead(Long userId);
}
