package com.example.agent.repository;

import com.example.agent.model.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsernameAndDeletedFalse(String username);
    Optional<AppUser> findByEmailAndDeletedFalse(String email);
    Optional<AppUser> findByPhoneAndDeletedFalse(String phone);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
}
