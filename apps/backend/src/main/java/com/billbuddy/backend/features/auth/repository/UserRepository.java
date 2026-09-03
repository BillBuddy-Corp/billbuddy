package com.billbuddy.backend.features.auth.repository;

import com.billbuddy.backend.features.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    boolean existsByProfilePicFile_Id(Long fileId);
}
