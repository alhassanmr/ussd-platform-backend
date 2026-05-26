package com.ussdplatform.repository;
import com.ussdplatform.model.UssdApp;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface UssdAppRepository extends JpaRepository<UssdApp, UUID> {
    List<UssdApp> findByTenantId(UUID tenantId);
    Optional<UssdApp> findByIdAndTenantId(UUID id, UUID tenantId);
    boolean existsByIdAndTenantId(UUID id, UUID tenantId);
    long countByStatus(UssdApp.AppStatus status);
    long countByTenantId(UUID tenantId);
}
