package com.ussdplatform.admin;

import com.ussdplatform.model.*;
import com.ussdplatform.notification.NotificationService;
import com.ussdplatform.repository.*;
import com.ussdplatform.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final TenantRepository tenantRepo;
    private final UssdAppRepository appRepo;
    private final UssdSessionRepository sessionRepo;
    private final SubscriptionRepository subscriptionRepo;
    private final InvoiceRepository invoiceRepo;
    private final UsageRecordRepository usageRepo;
    private final PlanRepository planRepo;
    private final AdminUserRepository adminUserRepo;
    private final NotificationLogRepository notificationLogRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // ─── Admin Auth ───────────────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> adminLogin(@RequestBody Map<String, String> req) {
        AdminUser admin = adminUserRepo.findByEmail(req.get("email")).orElse(null);
        if (admin == null || !passwordEncoder.matches(req.get("password"), admin.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
        String token = jwtService.generateAdminToken(admin);
        return ResponseEntity.ok(Map.of("token", token, "name", admin.getFullName()));
    }

    // ─── Platform Stats ───────────────────────────────────────────────────────

    @GetMapping("/stats")
    public Map<String, Object> getPlatformStats() {
        long totalTenants = tenantRepo.count();
        long activeTenants = tenantRepo.countByStatus(Tenant.TenantStatus.ACTIVE);
        long totalApps = appRepo.count();
        long activeApps = appRepo.countByStatus(UssdApp.AppStatus.ACTIVE);
        long totalSessions = sessionRepo.count();

        List<Invoice> paidInvoices = invoiceRepo.findByStatusAndCreatedAtAfter(
                Invoice.InvoiceStatus.PAID,
                LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0));
        double monthlyRevenue = paidInvoices.stream()
                .mapToDouble(i -> i.getAmountGhs().doubleValue())
                .sum();

        Map<String, Long> planDist = subscriptionRepo.findAll().stream()
                .collect(Collectors.groupingBy(
                        s -> s.getPlan().getName(), Collectors.counting()));

        return Map.of(
                "totalTenants", totalTenants,
                "activeTenants", activeTenants,
                "totalApps", totalApps,
                "activeApps", activeApps,
                "totalSessions", totalSessions,
                "monthlyRevenueGhs", monthlyRevenue,
                "planDistribution", planDist
        );
    }

    // ─── Tenant Management ────────────────────────────────────────────────────

    @GetMapping("/tenants")
    public List<Map<String, Object>> listTenants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {

        return tenantRepo.findAll().stream()
                .filter(t -> search == null
                        || t.getName().toLowerCase().contains(search.toLowerCase())
                        || t.getEmail().toLowerCase().contains(search.toLowerCase()))
                .skip((long) page * size)
                .limit(size)
                .map(t -> {
                    Optional<Subscription> sub = subscriptionRepo.findByTenantId(t.getId());
                    long sessionCount = usageRepo.sumSessionsByTenantAndPeriod(
                            t.getId(),
                            LocalDateTime.now().getYear(),
                            LocalDateTime.now().getMonthValue()).orElse(0L);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("name", t.getName());
                    m.put("email", t.getEmail());
                    m.put("slug", t.getSlug());
                    m.put("status", t.getStatus().name());
                    m.put("plan", sub.map(s -> s.getPlan().getName()).orElse("FREE"));
                    m.put("subscriptionStatus", sub.map(s -> s.getStatus().name()).orElse("TRIAL"));
                    m.put("sessionsThisMonth", sessionCount);
                    m.put("appCount", appRepo.countByTenantId(t.getId()));
                    m.put("createdAt", t.getCreatedAt());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @PutMapping("/tenants/{id}/status")
    public ResponseEntity<Map<String, String>> updateTenantStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> req) {

        return tenantRepo.findById(id)
                .map(t -> {
                    t.setStatus(Tenant.TenantStatus.valueOf(req.get("status")));
                    tenantRepo.save(t);
                    return ResponseEntity.ok(Map.of("status", t.getStatus().name()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/tenants/{id}/plan")
    public ResponseEntity<Map<String, Object>> changeTenantPlan(
            @PathVariable UUID id,
            @RequestBody Map<String, String> req) {

        Tenant tenant = tenantRepo.findById(id).orElse(null);
        if (tenant == null) return ResponseEntity.notFound().build();

        Plan plan = planRepo.findByName(req.get("plan").toUpperCase()).orElse(null);
        if (plan == null) return ResponseEntity.badRequest().body(Map.of("error", "Plan not found"));

        Subscription sub = subscriptionRepo.findByTenantId(id)
                .orElseGet(() -> Subscription.builder().tenant(tenant).build());
        sub.setPlan(plan);
        sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscriptionRepo.save(sub);

        return ResponseEntity.ok(Map.of("plan", plan.getName(), "message", "Plan updated"));
    }

    // ─── Revenue ──────────────────────────────────────────────────────────────

    @GetMapping("/invoices")
    public List<Map<String, Object>> listInvoices(@RequestParam(required = false) String status) {
        List<Invoice> invoices = status != null
                ? invoiceRepo.findByStatus(Invoice.InvoiceStatus.valueOf(status.toUpperCase()))
                : invoiceRepo.findAll();

        return invoices.stream()
                .sorted(Comparator.comparing(Invoice::getCreatedAt).reversed())
                .limit(100)
                .map(inv -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", inv.getId());
                    m.put("invoiceNumber", inv.getInvoiceNumber());
                    m.put("tenantName", inv.getTenant().getName());
                    m.put("amountGhs", inv.getAmountGhs());
                    m.put("status", inv.getStatus().name());
                    m.put("createdAt", inv.getCreatedAt());
                    m.put("paidAt", inv.getPaidAt() != null ? inv.getPaidAt().toString() : "");
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ─── Notification Logs ────────────────────────────────────────────────────

    @GetMapping("/notifications")
    public List<Map<String, Object>> listNotifications() {
        return notificationLogRepo.findAll().stream()
                .sorted(Comparator.comparing(NotificationLog::getSentAt).reversed())
                .limit(50)
                .map(n -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", n.getId());
                    m.put("recipient", n.getRecipientEmail());
                    m.put("type", n.getType());
                    m.put("status", n.getStatus());
                    m.put("sentAt", n.getSentAt());
                    return m;
                })
                .collect(Collectors.toList());
    }
}
