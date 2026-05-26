package com.ussdplatform.notification;

import com.ussdplatform.model.Invoice;
import com.ussdplatform.model.NotificationLog;
import com.ussdplatform.model.Tenant;
import com.ussdplatform.repository.NotificationLogRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final JavaMailSender mailSender;
    private final NotificationLogRepository notificationLogRepo;

    @Value("${spring.mail.from:}")
    private String fromEmail;

    @Value("${app.name:USSD Platform}")
    private String appName;

    @Value("${app.base-url:http://localhost:3000}")
    private String appBaseUrl;

    // ─── Public send methods ──────────────────────────────────────────────────

    @Async
    public void sendWelcome(Tenant tenant, String ownerName) {
        send(tenant, tenant.getEmail(),
                "Welcome to " + appName + " 🎉",
                welcomeTemplate(ownerName, tenant.getName()),
                "WELCOME");
    }

    @Async
    public void sendUsageWarning(Tenant tenant, int used, int limit, int percentage) {
        send(tenant, tenant.getEmail(),
                "[" + appName + "] You've used " + percentage + "% of your sessions",
                usageWarningTemplate(tenant.getName(), used, limit, percentage),
                "USAGE_WARNING");
    }

    @Async
    public void sendInvoice(Tenant tenant, Invoice invoice) {
        send(tenant, tenant.getEmail(),
                "[" + appName + "] Invoice " + invoice.getInvoiceNumber(),
                invoiceTemplate(tenant.getName(), invoice),
                "INVOICE");
    }

    @Async
    public void sendPaymentFailed(Tenant tenant, String invoiceNumber) {
        send(tenant, tenant.getEmail(),
                "[Action Required] Payment failed — " + appName,
                paymentFailedTemplate(tenant.getName(), invoiceNumber),
                "PAYMENT_FAILED");
    }

    @Async
    public void sendSubscriptionCancelled(Tenant tenant) {
        send(tenant, tenant.getEmail(),
                "Your " + appName + " subscription has been cancelled",
                cancellationTemplate(tenant.getName()),
                "SUSPENSION");
    }

    // ─── Core send ────────────────────────────────────────────────────────────

    private void send(Tenant tenant, String to, String subject, String htmlBody, String type) {
        NotificationLog entry = NotificationLog.builder()
                .tenant(tenant)
                .recipientEmail(to)
                .type(type)
                .subject(subject)
                .status("SENT")
                .build();

        // Skip sending if mail not configured
        if (fromEmail == null || fromEmail.isBlank()) {
            log.warn("Email not configured — skipping {} email to {}", type, to);
            entry.setStatus("SKIPPED");
            notificationLogRepo.save(entry);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent: {} → {}", type, to);
        } catch (Exception e) {
            entry.setStatus("FAILED");
            entry.setErrorMessage(e.getMessage());
            log.error("Failed to send {} email to {}: {}", type, to, e.getMessage());
        }

        notificationLogRepo.save(entry);
    }

    // ─── Templates ────────────────────────────────────────────────────────────

    private String welcomeTemplate(String name, String company) {
        return html("Welcome to " + appName + "!", String.format("""
            <h2 style="margin:0 0 16px">Welcome, %s! 👋</h2>
            <p>Your company <strong>%s</strong> is now set up on <strong>%s</strong>.</p>
            <p style="margin-top:16px">Here's how to get started:</p>
            <ol style="line-height:2">
              <li>Create your first USSD app from the dashboard</li>
              <li>Build your menu flow using the visual menu builder</li>
              <li>Copy your webhook URL and configure it in your gateway</li>
              <li>Test by dialling your short code</li>
            </ol>
            <p>You're on the <strong>Free plan</strong> — 500 sessions/month included.</p>
            <a href="%s" style="display:inline-block;margin-top:20px;background:#111;color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:500">
              Go to dashboard →
            </a>
            """, name, company, appName, appBaseUrl));
    }

    private String usageWarningTemplate(String company, int used, int limit, int pct) {
        String color = pct >= 100 ? "#dc2626" : "#d97706";
        return html("Usage Warning", String.format("""
            <h2 style="color:%s;margin:0 0 16px">⚠️ %d%% of sessions used</h2>
            <p><strong>%s</strong> has used <strong>%d of %d sessions</strong> this month.</p>
            <p style="margin-top:12px">%s</p>
            <a href="%s/billing" style="display:inline-block;margin-top:20px;background:#111;color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none">
              Upgrade plan →
            </a>
            """, color, pct, company, used, limit,
                pct >= 100
                    ? "You've reached your limit. Extra sessions will be charged at your overage rate."
                    : "Consider upgrading to avoid service interruption.",
                appBaseUrl));
    }

    private String invoiceTemplate(String company, Invoice invoice) {
        return html("Invoice " + invoice.getInvoiceNumber(), String.format("""
            <h2 style="margin:0 0 16px">Invoice %s</h2>
            <p>Hi <strong>%s</strong>, here is your invoice for %s.</p>
            <table style="width:100%%;border-collapse:collapse;margin:20px 0">
              <tr style="background:#f5f5f5">
                <th style="padding:10px;text-align:left;border-bottom:1px solid #eee">Description</th>
                <th style="padding:10px;text-align:right;border-bottom:1px solid #eee">Amount</th>
              </tr>
              <tr>
                <td style="padding:10px;border-bottom:1px solid #eee">Monthly subscription</td>
                <td style="padding:10px;text-align:right;border-bottom:1px solid #eee">GHS %.2f</td>
              </tr>
              <tr>
                <td style="padding:10px;font-weight:bold">Total</td>
                <td style="padding:10px;text-align:right;font-weight:bold">GHS %.2f</td>
              </tr>
            </table>
            <p>Status: <strong>%s</strong></p>
            """, invoice.getInvoiceNumber(), company, appName,
                invoice.getAmountGhs(), invoice.getAmountGhs(),
                invoice.getStatus().name()));
    }

    private String paymentFailedTemplate(String company, String invoiceNumber) {
        return html("Payment Failed", String.format("""
            <h2 style="color:#dc2626;margin:0 0 16px">⚠️ Payment Failed</h2>
            <p>Hi <strong>%s</strong>, we were unable to process payment for invoice <strong>%s</strong>.</p>
            <p style="margin-top:12px">Please update your payment method to avoid service interruption.</p>
            <a href="%s/billing" style="display:inline-block;margin-top:20px;background:#dc2626;color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none">
              Update payment →
            </a>
            """, company, invoiceNumber, appBaseUrl));
    }

    private String cancellationTemplate(String company) {
        return html("Subscription Cancelled", String.format("""
            <h2 style="margin:0 0 16px">Subscription Cancelled</h2>
            <p>Hi <strong>%s</strong>, your %s subscription has been cancelled.</p>
            <p style="margin-top:12px">Your account has been downgraded to the Free plan (500 sessions/month).</p>
            <a href="%s" style="display:inline-block;margin-top:20px;background:#111;color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none">
              Resubscribe →
            </a>
            """, company, appName, appBaseUrl));
    }

    private String html(String title, String content) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"><title>%s</title></head>
            <body style="font-family:system-ui,-apple-system,sans-serif;max-width:600px;margin:0 auto;padding:32px 24px;color:#1a1a1a;background:#fff">
              <div style="margin-bottom:24px;padding-bottom:16px;border-bottom:2px solid #f3f4f6">
                <span style="font-size:18px;font-weight:700">📡 %s</span>
              </div>
              %s
              <div style="margin-top:32px;padding-top:16px;border-top:1px solid #f3f4f6;font-size:12px;color:#9ca3af">
                © %s · You're receiving this because you have an account with us.
              </div>
            </body>
            </html>
            """, title, appName, content, appName);
    }
}
