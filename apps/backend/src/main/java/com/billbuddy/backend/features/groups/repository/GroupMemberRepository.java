package com.billbuddy.backend.features.groups.repository;

import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.model.GroupRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    Optional<GroupMember> findByGroup_IdAndUser_IdAndLeftAtIsNull(Long groupId, Long userId);

    // any state (active or previously left) — used to reuse the row on rejoin
    Optional<GroupMember> findByGroup_IdAndUser_Id(Long groupId, Long userId);

    List<GroupMember> findByGroup_IdAndLeftAtIsNull(Long groupId);

    List<GroupMember> findByUser_IdAndLeftAtIsNull(Long userId);

    long countByGroup_IdAndLeftAtIsNull(Long groupId);

    long countByGroup_IdAndRoleAndLeftAtIsNull(Long groupId, GroupRole role);
}
