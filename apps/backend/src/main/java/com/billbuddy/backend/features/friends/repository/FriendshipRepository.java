package com.billbuddy.backend.features.friends.repository;

import com.billbuddy.backend.features.friends.model.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    List<Friendship> findByUserLow_IdOrUserHigh_Id(Long userLowId, Long userHighId);

    Optional<Friendship> findByUserLow_IdAndUserHigh_Id(Long userLowId, Long userHighId);
}
