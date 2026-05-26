package com.ussdplatform.billing;

import com.ussdplatform.model.*;
import com.ussdplatform.notification.NotificationService;
import com.ussdplatform.repository.SubscriptionRepository;
import com.ussdplatform.repository.UsageRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsageTrackingService {

    private final UsageRecordRepository usageRepo;
    private final SubscriptionRepository subscriptionRepo;
    private final NotificationService notificationService;

    /**
     * Called after every successful USSD session.
     * Increments count, checks limits, triggers warnings.
     */
    @Transactional
    public void recordSession(Tenant tenant, UssdApp app) {
        int year = LocalDateTime.now().getYear();
        int month = LocalDateTime.now().getMonthValue();

        // Upsert usage record
        UsageRecord record = usageRepo
                .findByTenantIdAndAppIdAndPeriodYearAndPeriodMonth(
                        tenant.getId(), app.getId(), year, month)
                .orElseGet(() -> {
                    UsageRecord r = UsageRecord.builder()
                            .tenant(tenant)
                            .app(app)
                            .periodYear(year)
                            .periodMonth(month)
                            .sessionCount(0)
                            .build();
                    return usageRepo.save(r);
                });

        record.setSessionCount(record.getSessionCount() + 1);

        // Check plan limits
        Subscription sub = subscriptionRepo.findByTenantId(tenant.getId()).orElse(null);
        if (sub != null) {
            Plan plan = sub.getPlan();
            int planLimit = plan.getMaxSessions();

            if (planLimit > 0) {
                // Get total sessions this month across all apps
                long totalSessions = usageRepo
                        .sumSessionsByTenantAndPeriod(tenant.getId(), year, month)
                        .orElse(0L) + 1;

                // Track overage
                if (totalSessions > planLimit) {
                    record.setExtraSessions(record.getExtraSessions() + 1);
                    BigDecimal extraFee = plan.getExtraSessionFee();
                    record.setExtraCharges(record.getExtraCharges().add(extraFee));
                }

                // Send 80% warning email
                long eightyPercent = (long)(planLimit * 0.8);
                if (totalSessions == eightyPercent) {
                    notificationService.sendUsageWarning(tenant, (int) totalSessions, planLimit, 80);
                }
                // Send 100% warning
                if (totalSessions == planLimit) {
                    notificationService.sendUsageWarning(tenant, (int) totalSessions, planLimit, 100);
                }
            }
        }

        usageRepo.save(record);
    }

    /**
     * Get total sessions for a tenant this month.
     */
    public long getMonthlySessionCount(UUID tenantId) {
        int year = LocalDateTime.now().getYear();
        int month = LocalDateTime.now().getMonthValue();
        return usageRepo.sumSessionsByTenantAndPeriod(tenantId, year, month).orElse(0L);
    }

    /**
     * Check if tenant has exceeded their plan limit.
     */
    public boolean hasExceededLimit(UUID tenantId) {
        Subscription sub = subscriptionRepo.findByTenantId(tenantId).orElse(null);
        if (sub == null) return false;
        int limit = sub.getPlan().getMaxSessions();
        if (limit == -1) return false; // unlimited
        return getMonthlySessionCount(tenantId) >= limit;
    }
}
