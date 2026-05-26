package com.ussdplatform.notification;

import com.ussdplatform.model.PasswordResetToken;
import com.ussdplatform.model.User;
import com.ussdplatform.repository.PasswordResetTokenRepository;
import com.ussdplatform.repository.UserRepository;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final JavaMailSender mailSender;
    private final PasswordResetTokenRepository tokenRepo;
    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    @Value("${mailjet.from-email:hasfatempire@gmail.com}")
    private String fromEmail;

    @Value("${mailjet.from-name:Hasfatempire}")
    private String fromName;

    @Value("${app.base-url:http://localhost:3000}")
    private String appBaseUrl;

    @Value("${app.name:USSD Platform}")
    private String appName;

    @Async
    @Transactional
    public void sendResetEmail(String email) {
        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) {
            // Don't reveal if email exists - just log and return silently
            log.info("Password reset requested for unknown email: {}", email);
            return;
        }

        tokenRepo.deleteByUserId(user.getId());

        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .token(token)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        tokenRepo.save(resetToken);

        String resetUrl = appBaseUrl + "/reset-password?token=" + token;
        log.info("Password reset link generated for: {}", email);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromEmail, fromName));
            helper.setTo(email);
            helper.setSubject("Reset your " + appName + " password");
            helper.setText(buildResetEmail(user.getFullName(), resetUrl), true);
            mailSender.send(message);
            log.info("✓ Password reset email sent to: {}", email);
        } catch (Exception e) {
            log.error("✗ Failed to send reset email to {}: {}", email, e.getMessage());
        }
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepo.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset link."));

        if (resetToken.isUsed())    throw new IllegalArgumentException("This reset link has already been used.");
        if (resetToken.isExpired()) throw new IllegalArgumentException("This reset link has expired (1 hour). Please request a new one.");
        if (newPassword.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters.");

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);

        resetToken.setUsedAt(LocalDateTime.now());
        tokenRepo.save(resetToken);

        log.info("✓ Password reset successful for: {}", user.getEmail());
    }

    private String buildResetEmail(String name, String resetUrl) {
        return String.format("""
            <!DOCTYPE html><html>
            <head><meta charset="UTF-8"></head>
            <body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:40px 24px;color:#111">
              <div style="margin-bottom:28px;padding-bottom:20px;border-bottom:2px solid #f3f4f6">
                <span style="font-size:20px">📡</span>
                <span style="font-size:18px;font-weight:700;margin-left:8px">%s</span>
              </div>
              <h2 style="margin:0 0 16px">Reset your password</h2>
              <p>Hi <strong>%s</strong>, we received a request to reset your password.</p>
              <p style="margin-top:8px">Click below to set a new password. This link expires in <strong>1 hour</strong>.</p>
              <a href="%s" style="display:inline-block;margin:24px 0;background:#111;color:#fff;padding:14px 32px;border-radius:8px;text-decoration:none;font-weight:600">
                Reset password →
              </a>
              <p style="color:#6b7280;font-size:13px">Or copy: <span style="color:#3b82f6;word-break:break-all">%s</span></p>
              <p style="color:#6b7280;font-size:13px;margin-top:16px">
                If you didn't request this, you can safely ignore this email. Your password won't change.
              </p>
              <div style="margin-top:40px;padding-top:20px;border-top:1px solid #f3f4f6;font-size:12px;color:#9ca3af">© %s</div>
            </body></html>
            """, appName, name, resetUrl, resetUrl, appName);
    }
}
