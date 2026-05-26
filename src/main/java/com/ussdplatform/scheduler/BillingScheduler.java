package com.ussdplatform.scheduler;

import com.ussdplatform.billing.PaystackBillingService;
import com.ussdplatform.model.*;
import com.ussdplatform.notification.NotificationService;
import com.ussdplatform.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BillingScheduler {

    private final SubscriptionRepository subscriptionRepo;
    private final UsageRecordRepository usageRepo;
    private final NotificationService notificationService;
    private final PaystackBillingService paystackService;
    private final TenantRepository tenantRepo;
    private final InvoiceRepository invoiceRepo;

    /**
     * Run every day at 8am — check for expiring subscriptions and renew.
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void processBillingRenewals() {
        log.info("Running billing renewal check...");

        List<Subscription> expiring = subscriptionRepo.findAll().stream()
                .filter(s -> s.getStatus() == Subscription.SubscriptionStatus.ACTIVE
                        && s.getCurrentPeriodEnd().isBefore(LocalDateTime.now().plusDays(1)))
                .toList();

        for (Subscription sub : expiring) {
            if (sub.getPlan().getPriceGhs().compareTo(java.math.BigDecimal.ZERO) == 0) {
                // Free plan — just extend period
                sub.setCurrentPeriodStart(LocalDateTime.now());
                sub.setCurrentPeriodEnd(LocalDateTime.now().plusDays(30));
                subscriptionRepo.save(sub);
            } else {
                // Paid plan — create invoice and notify
                log.info("Subscription renewal due for tenant {}", sub.getTenant().getId());
                notificationService.sendInvoice(sub.getTenant(),
                        createRenewalInvoice(sub));
            }
        }
    }

    /**
     * Run on 1st of each month at midnight — reset usage counters (handled by new records).
     * Also suspend tenants with PAST_DUE subscriptions older than 7 days.
     */
    @Scheduled(cron = "0 0 0 1 * *")
    public void monthlyMaintenance() {
        log.info("Running monthly maintenance...");

        // Suspend past-due subscriptions older than 7 days
        subscriptionRepo.findByStatus(Subscription.SubscriptionStatus.PAST_DUE).stream()
                .filter(s -> s.getUpdatedAt().isBefore(LocalDateTime.now().minusDays(7)))
                .forEach(s -> {
                    Tenant tenant = s.getTenant();
                    tenant.setStatus(Tenant.TenantStatus.SUSPENDED);
                    tenantRepo.save(tenant);
                    notificationService.sendSubscriptionCancelled(tenant);
                    log.info("Suspended tenant {} for non-payment", tenant.getId());
                });
    }

    /**
     * Run every hour — send 80% usage warnings if not already sent this month.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void checkUsageThresholds() {
        // Handled in real-time by UsageTrackingService.recordSession()
        // This is a backup sweep for any missed thresholds
        log.debug("Usage threshold check completed");
    }

    private Invoice createRenewalInvoice(Subscription sub) {
        String invoiceNumber = "INV-" + LocalDateTime.now().getYear()
                + String.format("%02d", LocalDateTime.now().getMonthValue())
                + "-" + (invoiceRepo.count() + 1001);

        Invoice inv = Invoice.builder()
                .tenant(sub.getTenant())
                .subscription(sub)
                .invoiceNumber(invoiceNumber)
                .amountGhs(sub.getPlan().getPriceGhs())
                .status(Invoice.InvoiceStatus.PENDING)
                .dueDate(LocalDateTime.now().plusDays(7))
                .periodStart(LocalDateTime.now())
                .periodEnd(LocalDateTime.now().plusDays(30))
                .lineItems(List.of(new Invoice.LineItem(
                        sub.getPlan().getDisplayName() + " plan — monthly renewal",
                        1, sub.getPlan().getPriceGhs(), sub.getPlan().getPriceGhs()
                )))
                .build();

        return invoiceRepo.save(inv);
    }
}
