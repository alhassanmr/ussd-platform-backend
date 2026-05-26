package com.ussdplatform.repository;
import com.ussdplatform.model.SessionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SessionLogRepository extends JpaRepository<SessionLog, UUID> {
    List<SessionLog> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
