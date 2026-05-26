package com.ussdplatform.repository;
import com.ussdplatform.model.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {
    Optional<UsageRecord> findByTenantIdAndAppIdAndPeriodYearAndPeriodMonth(UUID tenantId, UUID appId, int year, int month);
    List<UsageRecord> findByTenantIdAndPeriodYearAndPeriodMonth(UUID tenantId, int year, int month);
    @Query("SELECT SUM(u.sessionCount) FROM UsageRecord u WHERE u.tenant.id = :tenantId AND u.periodYear = :year AND u.periodMonth = :month")
    Optional<Long> sumSessionsByTenantAndPeriod(UUID tenantId, int year, int month);
}
