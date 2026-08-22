package com.samaki.farm.auth.repository;

import com.samaki.farm.auth.entity.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {
    Optional<PasswordResetOtp> findFirstByUser_UserIdAndUsedAtIsNullOrderByCreatedAtDesc(UUID userId);
}
