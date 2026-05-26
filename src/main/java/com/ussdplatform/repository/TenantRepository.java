package com.ussdplatform.repository;
import com.ussdplatform.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    boolean existsBySlug(String slug);
    Optional<Tenant> findBySlug(String slug);
    Optional<Tenant> findByEmail(String email);
    long countByStatus(Tenant.TenantStatus status);
}
