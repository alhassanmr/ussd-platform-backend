package com.ussdplatform.repository;
import com.ussdplatform.model.UssdApp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UssdAppRepository extends JpaRepository<UssdApp, UUID> {
    // Exclude soft-deleted apps from all tenant queries
    @Query("SELECT a FROM UssdApp a WHERE a.tenant.id = :tenantId AND a.deletedAt IS NULL")
    List<UssdApp> findByTenantId(UUID tenantId);

    @Query("SELECT a FROM UssdApp a WHERE a.id = :id AND a.tenant.id = :tenantId AND a.deletedAt IS NULL")
    Optional<UssdApp> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT COUNT(a) > 0 FROM UssdApp a WHERE a.id = :id AND a.tenant.id = :tenantId AND a.deletedAt IS NULL")
    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByStatus(UssdApp.AppStatus status);

    @Query("SELECT COUNT(a) FROM UssdApp a WHERE a.tenant.id = :tenantId AND a.deletedAt IS NULL")
    long countByTenantId(UUID tenantId);

    // Admin only — see all including soft-deleted
    @Query("SELECT a FROM UssdApp a WHERE a.tenant.id = :tenantId ORDER BY a.deletedAt DESC NULLS FIRST")
    List<UssdApp> findAllByTenantIdIncludeDeleted(UUID tenantId);

    @Query("SELECT a FROM UssdApp a WHERE a.deletedAt IS NOT NULL ORDER BY a.deletedAt DESC")
    List<UssdApp> findAllSoftDeleted();
}
