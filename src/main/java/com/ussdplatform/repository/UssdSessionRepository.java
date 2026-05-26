package com.ussdplatform.repository;
import com.ussdplatform.model.UssdSession;
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
}
