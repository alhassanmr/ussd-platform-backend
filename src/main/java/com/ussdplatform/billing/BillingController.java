package com.ussdplatform.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ussdplatform.model.*;
import com.ussdplatform.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
@Slf4j
public class BillingController {

    private final PlanRepository planRepo;
    private final SubscriptionRepository subscriptionRepo;
    private final InvoiceRepository invoiceRepo;
    private final UsageRecordRepository usageRepo;
    private final PaystackBillingService paystackService;
    private final ObjectMapper objectMapper;

    @Value("${paystack.secret-key:sk_test_placeholder}")
    private String paystackSecretKey;

    @Value("${app.base-url:http://localhost:3000}")
    private String appBaseUrl;

    @GetMapping("/plans")
    public List<Map<String, Object>> listPlans() {
        return planRepo.findByIsActiveTrue().stream()
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.getId());
                    m.put("name", p.getName());
                    m.put("displayName", p.getDisplayName());
                    m.put("priceGhs", p.getPriceGhs());
                    m.put("maxApps", p.getMaxApps());
                    m.put("maxSessions", p.getMaxSessions());
                    m.put("extraSessionFee", p.getExtraSessionFee());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/subscription")
    public ResponseEntity<Map<String, Object>> getSubscription(@AuthenticationPrincipal User user) {
        Map<String, Object> result = new LinkedHashMap<>();
        subscriptionRepo.findByTenantId(user.getTenant().getId()).ifPresentOrElse(
            sub -> {
                result.put("id", sub.getId());
                result.put("planName", sub.getPlan().getName());
                result.put("planDisplayName", sub.getPlan().getDisplayName());
                result.put("status", sub.getStatus().name());
                result.put("currentPeriodStart", sub.getCurrentPeriodStart());
                result.put("currentPeriodEnd", sub.getCurrentPeriodEnd());
                result.put("maxSessions", sub.getPlan().getMaxSessions());
                result.put("priceGhs", sub.getPlan().getPriceGhs());
            },
            () -> {
                result.put("planName", "FREE");
                result.put("status", "TRIAL");
            }
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/usage")
    public Map<String, Object> getUsage(@AuthenticationPrincipal User user) {
        int year = LocalDateTime.now().getYear();
        int month = LocalDateTime.now().getMonthValue();
        long total = usageRepo.sumSessionsByTenantAndPeriod(
                user.getTenant().getId(), year, month).orElse(0L);
        int limit = subscriptionRepo.findByTenantId(user.getTenant().getId())
                .map(s -> s.getPlan().getMaxSessions()).orElse(500);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionsUsed", total);
        m.put("sessionsLimit", limit);
        m.put("percentageUsed", limit > 0 ? (int)((total * 100) / limit) : 0);
        m.put("periodYear", year);
        m.put("periodMonth", month);
        return m;
    }

    @PostMapping("/subscribe/{planName}")
    public ResponseEntity<Map<String, Object>> subscribe(
            @AuthenticationPrincipal User user,
            @PathVariable String planName) {
        Plan plan = planRepo.findByName(planName.toUpperCase()).orElse(null);
        if (plan == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "Plan not found");
            return ResponseEntity.badRequest().body(err);
        }
        if (plan.getPriceGhs().compareTo(java.math.BigDecimal.ZERO) == 0) {
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("message", "Switched to free plan");
            return ResponseEntity.ok(ok);
        }
        String authUrl = paystackService.initiatePayment(
                user.getTenant(), plan, appBaseUrl + "/billing/verify");
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("authorizationUrl", authUrl);
        return ResponseEntity.ok(ok);
    }

    @GetMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyPayment(@RequestParam String reference) {
        boolean success = paystackService.verifyAndActivate(reference);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", success);
        return ResponseEntity.ok(m);
    }

    @GetMapping("/invoices")
    public List<Map<String, Object>> getInvoices(@AuthenticationPrincipal User user) {
        return invoiceRepo.findByTenantIdOrderByCreatedAtDesc(user.getTenant().getId())
                .stream()
                .map(inv -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", inv.getId());
                    m.put("invoiceNumber", inv.getInvoiceNumber());
                    m.put("amountGhs", inv.getAmountGhs());
                    m.put("status", inv.getStatus().name());
                    m.put("createdAt", inv.getCreatedAt());
                    m.put("paidAt", inv.getPaidAt() != null ? inv.getPaidAt().toString() : "");
                    return m;
                })
                .collect(Collectors.toList());
    }

    @PostMapping("/paystack/webhook")
    public ResponseEntity<String> paystackWebhook(
            @RequestHeader("x-paystack-signature") String signature,
            @RequestBody String payload) {
        if (!verifySignature(payload, signature)) {
            return ResponseEntity.status(401).body("Invalid signature");
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            paystackService.handleWebhook(node.path("event").asText(), node.path("data"));
        } catch (Exception e) {
            log.error("Paystack webhook error", e);
        }
        return ResponseEntity.ok("OK");
    }

    private boolean verifySignature(String payload, String signature) {
        try {
            Mac sha512 = Mac.getInstance("HmacSHA512");
            sha512.init(new SecretKeySpec(paystackSecretKey.getBytes(), "HmacSHA512"));
            byte[] hash = sha512.doFinal(payload.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString().equals(signature);
        } catch (Exception e) {
            return false;
        }
    }
}
