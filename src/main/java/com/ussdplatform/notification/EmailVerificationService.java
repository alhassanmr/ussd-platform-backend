package com.ussdplatform.notification;

import com.ussdplatform.model.EmailVerificationToken;
import com.ussdplatform.model.User;
import com.ussdplatform.repository.EmailVerificationTokenRepository;
import com.ussdplatform.repository.UserRepository;
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
public class EmailVerificationService {

    private final JavaMailSender mailSender;
    private final EmailVerificationTokenRepository tokenRepo;
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
     * Generate a token and send verification email.
     * Called after registration — async so it doesn't block the response.
     */
    @Async
    @Transactional
    public void sendVerificationEmail(User user) {
        // Delete any existing unused tokens for this user
        tokenRepo.deleteByUserId(user.getId());

        // Create new token (expires in 24 hours)
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user)
                .token(token)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        tokenRepo.save(verificationToken);

        String verifyUrl = appBaseUrl + "/verify-email?token=" + token;
        log.info("Sending verification email to: {} | URL: {}", user.getEmail(), verifyUrl);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromEmail, fromName));
            helper.setTo(user.getEmail());
            helper.setSubject("Verify your " + appName + " account");
            helper.setText(buildEmailTemplate(user.getFullName(), verifyUrl), true);
            mailSender.send(message);
            log.info("✓ Verification email sent to: {}", user.getEmail());
        } catch (Exception e) {
            log.error("✗ Failed to send verification email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    /**
     * Validate token without consuming it.
     * Safe to call multiple times — used by GET endpoint.
     */
    public void validateTokenOnly(String token) {
        EmailVerificationToken verificationToken = tokenRepo.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification link. Please request a new one."));
        if (verificationToken.isUsed())    throw new IllegalArgumentException("This verification link has already been used. Please log in.");
        if (verificationToken.isExpired()) throw new IllegalArgumentException("This verification link has expired (24h). Please register again or request a new link.");
    }

    /**
     * Verify token and activate the user account.
     * Returns the user if successful, throws if invalid/expired.
     */
    @Transactional
    public User verifyToken(String token) {
        EmailVerificationToken verificationToken = tokenRepo.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification link. Please request a new one."));

        if (verificationToken.isUsed()) {
            throw new IllegalArgumentException("This verification link has already been used. Please log in.");
        }

        if (verificationToken.isExpired()) {
            throw new IllegalArgumentException("This verification link has expired (24h). Please register again or request a new link.");
        }

        // Activate user
        User user = verificationToken.getUser();
        user.setStatus(User.UserStatus.ACTIVE);
        userRepo.save(user);

        // Mark token as used
        verificationToken.setUsedAt(LocalDateTime.now());
        tokenRepo.save(verificationToken);

        log.info("✓ Email verified and account activated for: {}", user.getEmail());
        return user;
    }

    /**
     * Resend verification email — replaces old token.
     */
    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No account found with that email"));

        if (user.getStatus() == User.UserStatus.ACTIVE) {
            throw new IllegalArgumentException("Account is already verified");
        }

        sendVerificationEmail(user);
    }

    private String buildEmailTemplate(String name, String verifyUrl) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"><title>Verify your email</title></head>
            <body style="font-family:system-ui,-apple-system,sans-serif;max-width:600px;margin:0 auto;padding:40px 24px;color:#111;background:#fff">
              <div style="margin-bottom:28px;padding-bottom:20px;border-bottom:2px solid #f3f4f6">
                <span style="font-size:20px">📡</span>
                <span style="font-size:18px;font-weight:700;margin-left:8px">%s</span>
              </div>

              <h2 style="margin:0 0 16px;font-size:22px">Verify your email address</h2>
              <p style="color:#374151;line-height:1.6">Hi <strong>%s</strong>,</p>
              <p style="color:#374151;line-height:1.6;margin-top:8px">
                Thanks for signing up! Please verify your email address to activate your account.
                This link expires in <strong>24 hours</strong>.
              </p>

              <a href="%s"
                 style="display:inline-block;margin:28px 0;background:#111;color:#fff;padding:14px 32px;border-radius:8px;text-decoration:none;font-weight:600;font-size:15px">
                Verify my email →
              </a>

              <p style="color:#6b7280;font-size:13px;margin-top:8px">
                Or copy this link into your browser:<br>
                <span style="color:#3b82f6;word-break:break-all">%s</span>
              </p>

              <p style="color:#6b7280;font-size:13px;margin-top:24px">
                If you didn't create an account, you can safely ignore this email.
              </p>

              <div style="margin-top:40px;padding-top:20px;border-top:1px solid #f3f4f6;font-size:12px;color:#9ca3af">
                © %s · You received this because you signed up for an account.
              </div>
            </body>
            </html>
            """, appName, name, verifyUrl, verifyUrl, appName);
    }
}
