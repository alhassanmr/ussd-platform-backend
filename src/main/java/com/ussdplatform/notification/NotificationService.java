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

    @Value("${spring.mail.from:noreply@ussdplatform.com}")
    private String fromEmail;

    @Value("${app.name:USSD Platform}")
    private String appName;

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
            "[" + appName + "] You've used " + percentage + "% of your monthly sessions",
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

    private void send(Tenant tenant, String to, String subject, String htmlBody, String type) {
        NotificationLog entry = NotificationLog.builder()
                .tenant(tenant)
                .recipientEmail(to)
                .type(type)
                .subject(subject)
                .status("SENT")
                .build();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (Exception e) {
            entry.setStatus("FAILED");
            entry.setErrorMessage(e.getMessage());
            log.error("Failed to send {} email to {}: {}", type, to, e.getMessage());
        }
        notificationLogRepo.save(entry);
    }

    private String welcomeTemplate(String name, String company) {
        return html("Welcome!", String.format("""
            <h2>Welcome, %s!</h2>
            <p>Your company <strong>%s</strong> is now set up on USSD Platform.</p>
            <ol>
              <li>Create your first USSD app from the dashboard</li>
              <li>Build your menu flow using the visual menu builder</li>
              <li>Copy your webhook URL and configure it in your gateway</li>
              <li>Test by dialling your short code</li>
            </ol>
            <p>You're on the <strong>Free plan</strong> with 500 sessions/month.</p>
            """, name, company));
    }

    private String usageWarningTemplate(String company, int used, int limit, int pct) {
        return html("Usage Warning", String.format("""
            <h2>You've used %d%% of your monthly sessions</h2>
            <p><strong>%s</strong> has used <strong>%d of %d sessions</strong> this month.</p>
            <p>Consider upgrading your plan to avoid interruptions.</p>
            """, pct, company, used, limit));
    }

    private String invoiceTemplate(String company, Invoice invoice) {
        return html("Invoice " + invoice.getInvoiceNumber(), String.format("""
            <h2>Invoice %s</h2>
            <p>Hi <strong>%s</strong>, your invoice for USSD Platform.</p>
            <p>Amount: <strong>GHS %.2f</strong> — Status: %s</p>
            """, invoice.getInvoiceNumber(), company,
            invoice.getAmountGhs(), invoice.getStatus().name()));
    }

    private String paymentFailedTemplate(String company, String invoiceNumber) {
        return html("Payment Failed", String.format("""
            <h2>Payment Failed</h2>
            <p>Hi <strong>%s</strong>, we were unable to process payment for invoice <strong>%s</strong>.</p>
            <p>Please update your payment method to avoid service interruption.</p>
            """, company, invoiceNumber));
    }

    private String cancellationTemplate(String company) {
        return html("Subscription Cancelled", String.format("""
            <h2>Subscription Cancelled</h2>
            <p>Hi <strong>%s</strong>, your subscription has been cancelled.</p>
            <p>Your account has been downgraded to the Free plan.</p>
            """, company));
    }

    private String html(String title, String content) {
        return String.format("""
            <!DOCTYPE html><html><head><meta charset="UTF-8"><title>%s</title></head>
            <body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:32px 24px">
              <div style="margin-bottom:24px"><strong>📡 USSD Platform</strong></div>
              %s
              <div style="margin-top:32px;font-size:12px;color:#888">© USSD Platform</div>
            </body></html>
            """, title, content);
    }
}
