package com.billbuddy.backend.features.groups.repository;

import com.billbuddy.backend.features.groups.model.GroupInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface  GroupInviteRepository extends JpaRepository<GroupInvite, Long> {

    Optional<GroupInvite> findByToken(String token);

    Optional<GroupInvite> findByIdAndGroup_Id(Long id, Long groupId);

    List<GroupInvite> findByGroup_IdAndRevokedFalse(Long groupId);

    @Modifying
    @Query("""
        update GroupInvite gi
        set gi.revoked = true
        where gi.group.id = :groupId
          and gi.type = com.billbuddy.backend.features.groups.model.GroupInviteType.LINK
          and gi.revoked = false
    """)
    int revokeActiveLinkInvites(Long groupId);

    @Modifying
    @Query("""
        update GroupInvite gi
        set gi.revoked = true
        where gi.group.id = :groupId
          and gi.type = com.billbuddy.backend.features.groups.model.GroupInviteType.EMAIL
          and gi.email = :email
          and gi.revoked = false
          and gi.acceptedAt is null
    """)
    int revokeActivePendingEmailInvites(Long groupId, String email);

    @Modifying
    @Query("""
        update GroupInvite gi
        set gi.revoked = true
        where gi.group.id = :groupId
          and gi.revoked = false
    """)
    int revokeAllActiveInvites(Long groupId);
}
