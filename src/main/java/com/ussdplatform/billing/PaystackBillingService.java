package com.ussdplatform.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ussdplatform.model.*;
import com.ussdplatform.notification.NotificationService;
import com.ussdplatform.repository.InvoiceRepository;
import com.ussdplatform.repository.PlanRepository;
import com.ussdplatform.repository.SubscriptionRepository;
import com.ussdplatform.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaystackBillingService {

    private final WebClient.Builder webClientBuilder;
    private final SubscriptionRepository subscriptionRepo;
    private final InvoiceRepository invoiceRepo;
    private final PlanRepository planRepo;
    private final TenantRepository tenantRepo;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Value("${paystack.secret-key:sk_test_placeholder}")
    private String paystackSecretKey;

    private static final String PAYSTACK_BASE = "https://api.paystack.co";

    /**
     * Initialize a Paystack payment for a plan subscription.
     * Returns the authorization URL to redirect the user to.
     */
    public String initiatePayment(Tenant tenant, Plan plan, String callbackUrl) {
        try {
            // Amount in kobo (GHS pesewas) — Paystack uses smallest currency unit
            long amountPesewas = plan.getPriceGhs().multiply(BigDecimal.valueOf(100)).longValue();

            String reference = "USSD-" + tenant.getId().toString().substring(0, 8).toUpperCase()
                    + "-" + System.currentTimeMillis();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("email", tenant.getEmail());
            body.put("amount", amountPesewas);
            body.put("currency", "GHS");
            body.put("reference", reference);
            body.put("callback_url", callbackUrl);
            body.put("metadata", Map.of(
                    "tenant_id", tenant.getId().toString(),
                    "plan_id", plan.getId().toString(),
                    "plan_name", plan.getName()
            ));

            String response = paystackPost("/transaction/initialize", body);
            JsonNode node = objectMapper.readTree(response);

            if (node.path("status").asBoolean()) {
                String authUrl = node.path("data").path("authorization_url").asText();

                // Create a pending invoice
                createPendingInvoice(tenant, plan, reference);

                return authUrl;
            } else {
                throw new RuntimeException("Paystack error: " + node.path("message").asText());
            }
        } catch (Exception e) {
            log.error("Failed to initiate Paystack payment for tenant {}", tenant.getId(), e);
            throw new RuntimeException("Payment initiation failed", e);
        }
    }

    /**
     * Verify a Paystack payment by reference and activate subscription.
     */
    @Transactional
    public boolean verifyAndActivate(String reference) {
        try {
            String response = paystackGet("/transaction/verify/" + reference);
            JsonNode node = objectMapper.readTree(response);

            if (!node.path("status").asBoolean()) return false;

            JsonNode data = node.path("data");
            String status = data.path("status").asText();

            if (!"success".equals(status)) return false;

            // Extract metadata
            JsonNode meta = data.path("metadata");
            UUID tenantId = UUID.fromString(meta.path("tenant_id").asText());
            UUID planId = UUID.fromString(meta.path("plan_id").asText());
            String txnId = data.path("id").asText();

            // Update invoice
            invoiceRepo.findByPaystackRef(reference).ifPresent(inv -> {
                inv.setStatus(Invoice.InvoiceStatus.PAID);
                inv.setPaidAt(LocalDateTime.now());
                inv.setPaystackTxnId(txnId);
                invoiceRepo.save(inv);
            });

            // Activate / update subscription
            Plan plan = planRepo.findById(planId).orElseThrow();
            activateSubscription(tenantId, plan, data.path("customer").path("customer_code").asText());

            return true;
        } catch (Exception e) {
            log.error("Payment verification failed for ref {}", reference, e);
            return false;
        }
    }

    /**
     * Handle Paystack webhook events.
     */
    @Transactional
    public void handleWebhook(String event, JsonNode data) {
        log.info("Paystack webhook: {}", event);
        switch (event) {
            case "charge.success" -> verifyAndActivate(data.path("reference").asText());
            case "subscription.disable" -> {
                String subCode = data.path("subscription_code").asText();
                subscriptionRepo.findByPaystackSubCode(subCode).ifPresent(sub -> {
                    sub.setStatus(Subscription.SubscriptionStatus.CANCELLED);
                    subscriptionRepo.save(sub);
                    // Downgrade tenant to FREE
                    planRepo.findByName("FREE").ifPresent(freePlan -> {
                        sub.setPlan(freePlan);
                        subscriptionRepo.save(sub);
                    });
                    notificationService.sendSubscriptionCancelled(sub.getTenant());
                });
            }
            case "invoice.payment_failed" -> {
                String customerEmail = data.path("customer").path("email").asText();
                log.warn("Payment failed for customer: {}", customerEmail);
            }
        }
    }

    private void activateSubscription(UUID tenantId, Plan plan, String paystackCustomerId) {
        Tenant tenant = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));
        Subscription sub = subscriptionRepo.findByTenantId(tenantId)
                .orElseGet(() -> Subscription.builder().tenant(tenant).build());

        sub.setPlan(plan);
        sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        sub.setPaystackCustomerId(paystackCustomerId);
        sub.setCurrentPeriodStart(LocalDateTime.now());
        sub.setCurrentPeriodEnd(LocalDateTime.now().plusDays(30));
        subscriptionRepo.save(sub);
    }

    private void createPendingInvoice(Tenant tenant, Plan plan, String reference) {
        String invoiceNumber = "INV-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"))
                + "-" + (invoiceRepo.count() + 1001);

        Invoice inv = Invoice.builder()
                .tenant(tenant)
                .invoiceNumber(invoiceNumber)
                .amountGhs(plan.getPriceGhs())
                .status(Invoice.InvoiceStatus.PENDING)
                .paystackRef(reference)
                .dueDate(LocalDateTime.now().plusDays(3))
                .periodStart(LocalDateTime.now())
                .periodEnd(LocalDateTime.now().plusDays(30))
                .lineItems(List.of(new Invoice.LineItem(
                        plan.getDisplayName() + " plan — monthly subscription",
                        1, plan.getPriceGhs(), plan.getPriceGhs()
                )))
                .build();

        invoiceRepo.save(inv);
    }

    private String paystackPost(String path, Map<String, Object> body) throws Exception {
        return webClientBuilder.build()
                .post().uri(PAYSTACK_BASE + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + paystackSecretKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private String paystackGet(String path) {
        return webClientBuilder.build()
                .get().uri(PAYSTACK_BASE + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + paystackSecretKey)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
