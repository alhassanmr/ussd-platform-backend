package com.ussdplatform.repository;
import com.ussdplatform.model.UssdSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface UssdSessionRepository extends JpaRepository<UssdSession, UUID> {
    Optional<UssdSession> findBySessionId(String sessionId);
    List<UssdSession> findByAppIdOrderByStartedAtDesc(UUID appId);
    @Query("SELECT COUNT(s) FROM UssdSession s WHERE s.app.id = :appId AND s.status = 'ACTIVE'")
    long countActiveSessions(UUID appId);

    @Query("SELECT COUNT(s) FROM UssdSession s WHERE s.app.tenant.id = :tenantId")
    long countByTenantId(UUID tenantId);

    @Query("SELECT COUNT(s) FROM UssdSession s WHERE s.app.tenant.id = :tenantId AND s.status = 'ACTIVE'")
    long countActiveByTenantId(UUID tenantId);

    @Query("SELECT COUNT(s) FROM UssdSession s WHERE s.app.id = :appId")
    long countByAppId(UUID appId);

    @Query("SELECT COUNT(s) FROM UssdSession s WHERE s.app.tenant.id = :tenantId AND s.startedAt BETWEEN :from AND :to")
    long countByTenantIdAndDate(UUID tenantId, java.time.LocalDateTime from, java.time.LocalDateTime to);

    @Query("SELECT COUNT(s) FROM UssdSession s WHERE s.app.tenant.id = :tenantId AND s.status = :status")
    long countByTenantIdAndStatus(UUID tenantId, String status);

    @Query("SELECT s FROM UssdSession s WHERE s.app.tenant.id = :tenantId ORDER BY s.startedAt DESC")
    java.util.List<com.ussdplatform.model.UssdSession> findByTenantIdOrderByStartedAtDesc(
            UUID tenantId, org.springframework.data.domain.Pageable pageable);

    default java.util.List<com.ussdplatform.model.UssdSession> findByTenantIdOrderByStartedAtDesc(UUID tenantId, int limit) {
        return findByTenantIdOrderByStartedAtDesc(tenantId,
                org.springframework.data.domain.PageRequest.of(0, limit));
    }
}
