package com.ussdplatform.notification;

import com.ussdplatform.model.*;
import com.ussdplatform.repository.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamInviteService {

    private final JavaMailSender mailSender;
    private final TeamInviteRepository inviteRepo;
    private final UserRepository userRepo;

    @Value("${mailjet.from-email:hasfatempire@gmail.com}")
    private String fromEmail;

    @Value("${mailjet.from-name:Hasfatempire}")
    private String fromName;

    @Value("${app.base-url:http://localhost:3000}")
    private String appBaseUrl;

    @Value("${app.name:USSD Platform}")
    private String appName;

    /**
     * Send an invitation email to join the tenant's team.
     */
    @Async
    @Transactional
    public void sendInvite(Tenant tenant, User invitedBy, String email, User.Role role) {
        // Delete any pending invite for this email in this tenant
        inviteRepo.deleteByTenantIdAndEmail(tenant.getId(), email);

        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");

        TeamInvite invite = TeamInvite.builder()
                .tenant(tenant)
                .invitedBy(invitedBy)
                .email(email)
                .role(role)
                .token(token)
                .expiresAt(LocalDateTime.now().plusHours(48))
                .build();
        inviteRepo.save(invite);

        String inviteUrl = appBaseUrl + "/accept-invite?token=" + token;
        log.info("Sending team invite to {} for tenant {}", email, tenant.getName());

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromEmail, fromName));
            helper.setTo(email);
            helper.setSubject(invitedBy.getFullName() + " invited you to join " + tenant.getName() + " on " + appName);
            helper.setText(buildInviteEmail(invitedBy.getFullName(), tenant.getName(), role.name(), inviteUrl), true);
            mailSender.send(message);
            log.info("✓ Invite sent to {}", email);
        } catch (Exception e) {
            log.error("✗ Failed to send invite to {}: {}", email, e.getMessage());
            throw new RuntimeException("Failed to send invite email: " + e.getMessage());
        }
    }

    /**
     * Accept an invite — create user account and mark invite as accepted.
     */
    @Transactional
    public TeamInvite validateInvite(String token) {
        TeamInvite invite = inviteRepo.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite link."));

        if (invite.isAccepted()) {
            throw new IllegalArgumentException("This invite has already been used.");
        }
        if (invite.isExpired()) {
            throw new IllegalArgumentException("This invite has expired (48 hours). Please ask for a new invite.");
        }

        // Check if email already has an account in this tenant
        if (userRepo.existsByEmail(invite.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists. Please log in.");
        }

        return invite;
    }

    @Transactional
    public void markAccepted(TeamInvite invite) {
        invite.setAcceptedAt(LocalDateTime.now());
        inviteRepo.save(invite);
    }

    private String buildInviteEmail(String inviterName, String company, String role, String inviteUrl) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="font-family:system-ui,-apple-system,sans-serif;max-width:600px;margin:0 auto;padding:40px 24px;color:#111;background:#fff">
              <div style="margin-bottom:28px;padding-bottom:20px;border-bottom:2px solid #f3f4f6">
                <span style="font-size:20px">📡</span>
                <span style="font-size:18px;font-weight:700;margin-left:8px">%s</span>
              </div>

              <h2 style="margin:0 0 16px">You've been invited! 🎉</h2>
              <p style="color:#374151;line-height:1.7;margin:0 0 16px">
                <strong>%s</strong> has invited you to join <strong>%s</strong> on %s as a <strong>%s</strong>.
              </p>
              <p style="color:#374151;line-height:1.7;margin:0 0 28px">
                Click the button below to set up your account. This invite expires in <strong>48 hours</strong>.
              </p>

              <a href="%s"
                 style="display:inline-block;background:#111;color:#fff;padding:14px 32px;border-radius:8px;text-decoration:none;font-weight:600;font-size:15px">
                Accept invitation →
              </a>

              <p style="color:#6b7280;font-size:13px;margin-top:24px">
                Or copy this link:<br>
                <span style="color:#3b82f6;word-break:break-all">%s</span>
              </p>

              <p style="color:#6b7280;font-size:13px;margin-top:20px">
                If you weren't expecting this invite, you can safely ignore it.
              </p>

              <div style="margin-top:40px;padding-top:20px;border-top:1px solid #f3f4f6;font-size:12px;color:#9ca3af">
                © %s
              </div>
            </body>
            </html>
            """, appName, inviterName, company, appName, role, inviteUrl, inviteUrl, appName);
    }
}
