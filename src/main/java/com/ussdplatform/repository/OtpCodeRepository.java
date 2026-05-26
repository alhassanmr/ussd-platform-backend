package com.ussdplatform.repository;

import com.ussdplatform.model.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

    @Query("SELECT o FROM OtpCode o WHERE o.user.id = :userId AND o.usedAt IS NULL ORDER BY o.createdAt DESC")
    Optional<OtpCode> findLatestActiveByUserId(UUID userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM OtpCode o WHERE o.user.id = :userId")
    void deleteByUserId(UUID userId);
}
