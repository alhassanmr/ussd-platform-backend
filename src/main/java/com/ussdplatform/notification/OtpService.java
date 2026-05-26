package com.ussdplatform.notification;

import com.ussdplatform.model.OtpCode;
import com.ussdplatform.model.User;
import com.ussdplatform.repository.OtpCodeRepository;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final JavaMailSender mailSender;
    private final OtpCodeRepository otpRepo;

    @Value("${mailjet.from-email:hasfatempire@gmail.com}")
    private String fromEmail;

    @Value("${mailjet.from-name:Hasfatempire}")
    private String fromName;

    @Value("${app.name:USSD Platform}")
    private String appName;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generate OTP, save it, and send to user's email.
     * Called after successful password check.
     */
    @Transactional
    public void sendOtp(User user) {
        // Delete any existing OTPs for this user
        otpRepo.deleteByUserId(user.getId());

        // Generate 6-digit OTP
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        OtpCode otp = OtpCode.builder()
                .user(user)
                .code(code)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .attempts(0)
                .build();
        otpRepo.save(otp);

        log.info("OTP generated for: {} (expires in 10 mins)", user.getEmail());

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromEmail, fromName));
            helper.setTo(user.getEmail());
            helper.setSubject(appName + " — Your login code: " + code);
            helper.setText(buildOtpEmail(user.getFullName(), code), true);
            mailSender.send(message);
            log.info("✓ OTP email sent to: {}", user.getEmail());
        } catch (Exception e) {
            log.error("✗ Failed to send OTP to {}: {}", user.getEmail(), e.getMessage());
            throw new RuntimeException("Failed to send OTP email. Please try again.");
        }
    }

    /**
     * Verify the OTP code entered by the user.
     * Returns the user if valid, throws if invalid/expired/max attempts.
     */
    @Transactional
    public User verifyOtp(String email, String code, com.ussdplatform.repository.UserRepository userRepo) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid session. Please login again."));

        OtpCode otp = otpRepo.findLatestActiveByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("No active OTP found. Please login again to get a new code."));

        if (otp.isMaxAttemptsReached()) {
            throw new IllegalArgumentException("Too many incorrect attempts. Click Resend below to get a new code.");
        }

        if (otp.isExpired()) {
            throw new IllegalArgumentException("OTP has expired (10 minutes). Please login again to get a new code.");
        }

        if (otp.isUsed()) {
            throw new IllegalArgumentException("OTP already used. Please login again.");
        }

        // Increment attempt counter
        otp.setAttempts(otp.getAttempts() + 1);

        if (!otp.getCode().equals(code.trim())) {
            otpRepo.save(otp);
            int remaining = 3 - otp.getAttempts();
            throw new IllegalArgumentException(
                remaining > 0
                    ? "Incorrect code. " + remaining + " attempt" + (remaining == 1 ? "" : "s") + " remaining."
                    : "Too many incorrect attempts. Click Resend to get a new code."
            );
        }

        // Mark as used
        otp.setUsedAt(LocalDateTime.now());
        otpRepo.save(otp);

        log.info("✓ OTP verified for: {}", user.getEmail());
        return user;
    }

    private String buildOtpEmail(String name, String code) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="font-family:system-ui,-apple-system,sans-serif;max-width:600px;margin:0 auto;padding:40px 24px;color:#111;background:#fff">
              <div style="margin-bottom:28px;padding-bottom:20px;border-bottom:2px solid #f3f4f6">
                <span style="font-size:20px">📡</span>
                <span style="font-size:18px;font-weight:700;margin-left:8px">%s</span>
              </div>

              <h2 style="margin:0 0 8px;font-size:22px">Your login code</h2>
              <p style="color:#374151;margin:0 0 28px">Hi <strong>%s</strong>, use the code below to complete your login.</p>

              <div style="background:#f9fafb;border:2px dashed #e5e7eb;border-radius:12px;padding:28px;text-align:center;margin-bottom:24px">
                <div style="font-size:42px;font-weight:800;letter-spacing:10px;color:#111;font-family:monospace">%s</div>
                <p style="margin:12px 0 0;color:#6b7280;font-size:13px">Expires in <strong>10 minutes</strong> · Max 3 attempts</p>
              </div>

              <p style="color:#6b7280;font-size:13px;line-height:1.6">
                If you didn't try to log in, someone may have your password.
                <strong>Do not share this code with anyone.</strong>
              </p>

              <div style="margin-top:40px;padding-top:20px;border-top:1px solid #f3f4f6;font-size:12px;color:#9ca3af">
                © %s · This code expires in 10 minutes.
              </div>
            </body>
            </html>
            """, appName, name, code, appName);
    }
}
