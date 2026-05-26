package com.ussdplatform.controller;

import com.ussdplatform.model.User;
import com.ussdplatform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final UssdSessionRepository sessionRepo;
    private final UssdAppRepository appRepo;
    private final UsageRecordRepository usageRepo;

    @GetMapping("/overview")
    public Map<String, Object> overview(@AuthenticationPrincipal User user) {
        UUID tenantId = user.getTenant().getId();
        int year  = LocalDateTime.now().getYear();
        int month = LocalDateTime.now().getMonthValue();

        long totalApps    = appRepo.countByTenantId(tenantId);
        long totalSessions = sessionRepo.countByTenantId(tenantId);
        long activeSessions = sessionRepo.countActiveByTenantId(tenantId);
        long monthSessions = usageRepo.sumSessionsByTenantAndPeriod(tenantId, year, month).orElse(0L);

        // Sessions per app
        List<Map<String, Object>> perApp = appRepo.findByTenantId(tenantId).stream()
                .map(app -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("appId",   app.getId());
                    m.put("appName", app.getName());
                    m.put("status",  app.getStatus().name());
                    m.put("sessions", sessionRepo.countByAppId(app.getId()));
                    m.put("sessionsThisMonth", usageRepo
                            .sumSessionsByTenantAndPeriod(tenantId, year, month).orElse(0L));
                    return m;
                })
                .collect(Collectors.toList());

        // Last 7 days daily counts
        List<Map<String, Object>> daily = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDateTime day = LocalDateTime.now().minusDays(i);
            long count = sessionRepo.countByTenantIdAndDate(tenantId,
                    day.toLocalDate().atStartOfDay(),
                    day.toLocalDate().atTime(23, 59, 59));
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("date",  day.toLocalDate().toString());
            d.put("count", count);
            daily.add(d);
        }

        // Session status breakdown
        long completed = sessionRepo.countByTenantIdAndStatus(tenantId, "COMPLETED");
        long timeout   = sessionRepo.countByTenantIdAndStatus(tenantId, "TIMEOUT");
        long active    = sessionRepo.countByTenantIdAndStatus(tenantId, "ACTIVE");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalApps",       totalApps);
        result.put("totalSessions",   totalSessions);
        result.put("activeSessions",  activeSessions);
        result.put("monthSessions",   monthSessions);
        result.put("perApp",          perApp);
        result.put("daily",           daily);
        result.put("statusBreakdown", Map.of(
                "completed", completed,
                "timeout",   timeout,
                "active",    active
        ));
        return result;
    }

    @GetMapping("/sessions")
    public List<Map<String, Object>> recentSessions(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "50") int limit) {

        return sessionRepo.findByTenantIdOrderByStartedAtDesc(user.getTenant().getId(), limit)
                .stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",        s.getId());
                    m.put("msisdn",    maskPhone(s.getMsisdn()));
                    m.put("appName",   s.getApp().getName());
                    m.put("status",    s.getStatus().name());
                    m.put("startedAt", s.getStartedAt());
                    m.put("endedAt",   s.getEndedAt());
                    m.put("duration",  s.getEndedAt() != null
                            ? java.time.Duration.between(s.getStartedAt(), s.getEndedAt()).getSeconds()
                            : null);
                    return m;
                })
                .collect(Collectors.toList());
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) return phone;
        return phone.substring(0, phone.length() - 4) + "****";
    }
}
