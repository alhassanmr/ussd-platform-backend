package com.ussdplatform.repository;

import com.ussdplatform.model.TeamInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamInviteRepository extends JpaRepository<TeamInvite, UUID> {
    Optional<TeamInvite> findByToken(String token);
    List<TeamInvite> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<TeamInvite> findByTenantIdAndEmail(UUID tenantId, String email);
    boolean existsByTenantIdAndEmailAndAcceptedAtIsNull(UUID tenantId, String email);

    @Modifying
    @Transactional
    @Query("DELETE FROM TeamInvite i WHERE i.tenant.id = :tenantId AND i.email = :email")
    void deleteByTenantIdAndEmail(UUID tenantId, String email);
}
