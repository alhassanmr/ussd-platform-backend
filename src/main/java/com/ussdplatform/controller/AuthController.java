package com.ussdplatform.controller;

import com.ussdplatform.dto.*;
import com.ussdplatform.model.Tenant;
import com.ussdplatform.model.User;
import com.ussdplatform.notification.EmailVerificationService;
import com.ussdplatform.notification.NotificationService;
import com.ussdplatform.notification.OtpService;
import com.ussdplatform.repository.TenantRepository;
import com.ussdplatform.repository.UserRepository;
import com.ussdplatform.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final NotificationService notificationService;
    private final EmailVerificationService emailVerificationService;
    private final OtpService otpService;

    // ─── Step 1: Register ─────────────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("━━━ REGISTER ━━━ email={}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new AuthResponse(null, null, "Email already registered"));
        }

        String slug = request.getCompanyName().toLowerCase()
                .replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-");
        String finalSlug = slug;
        int attempt = 0;
        while (tenantRepository.existsBySlug(finalSlug)) {
            finalSlug = slug + "-" + (++attempt);
        }

        Tenant tenant = Tenant.builder()
                .name(request.getCompanyName()).slug(finalSlug)
                .email(request.getEmail()).phone(request.getPhone())
                .status(Tenant.TenantStatus.TRIAL).plan(Tenant.Plan.FREE)
                .build();
        tenantRepository.save(tenant);

        User user = User.builder()
                .tenant(tenant).email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(User.Role.OWNER)
                .status(User.UserStatus.PENDING)  // must verify email
                .build();
        userRepository.save(user);

        emailVerificationService.sendVerificationEmail(user);
        log.info("  ✓ Registered {} — verification email sent", request.getEmail());

        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(null, null,
                "Account created! Please check your email and click the verification link to activate your account."));
    }

    // ─── Step 2a: Login (password check → send OTP) ───────────────────────────

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        String identifier = request.getEmail();
        log.info("━━━ LOGIN STEP 1 ━━━ identifier={}", identifier);

        // Accept email OR phone number
        User user = null;
        if (identifier != null && identifier.startsWith("+")) {
            // Looks like a phone number
            user = userRepository.findByPhone(identifier).orElse(null);
            log.info("  Phone login attempt for: {}", identifier);
        } else {
            // Try email first
            user = userRepository.findByEmail(identifier).orElse(null);
            if (user == null && identifier != null && identifier.matches("\\d+")) {
                // Pure digits — try as phone with no country code (last resort)
                log.info("  Trying numeric identifier as phone: {}", identifier);
            }
        }

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("  ✗ Invalid credentials for: {}", identifier);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email/phone or password"));
        }

        if (user.getStatus() == User.UserStatus.PENDING) {
            log.warn("  ✗ Email not verified: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                        "error", "Please verify your email before logging in. Check your inbox for the verification link.",
                        "needsVerification", true,
                        "email", request.getEmail()
                    ));
        }

        if (user.getStatus() == User.UserStatus.INACTIVE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Your account has been deactivated. Please contact support."));
        }

        // Password correct + email verified → send OTP
        try {
            otpService.sendOtp(user);
            log.info("  ✓ OTP sent to: {}", user.getEmail());
            return ResponseEntity.ok(Map.of(
                    "otpRequired", true,
                    "email", user.getEmail(),
                    "message", "A 6-digit code has been sent to " + user.getEmail() + ". Enter it below to complete login."
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Step 2b: Verify OTP → issue JWT ─────────────────────────────────────

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> req) {
        String email = req.get("email");
        String code  = req.get("code");
        log.info("━━━ LOGIN STEP 2 (OTP) ━━━ email={} code={}",
                email, code != null ? "****" + code.substring(Math.max(0, code.length()-2)) : "null");

        if (email == null || code == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and code are required"));
        }

        try {
            User user = otpService.verifyOtp(email, code, userRepository);
            String token = jwtService.generateToken(user);
            log.info("  ✓ OTP verified — login complete for: {}", email);

            AuthResponse res = new AuthResponse(token, toUserDto(user), null);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            log.warn("  ✗ OTP verification failed for {}: {}", email, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Email Verification ───────────────────────────────────────────────────

    @GetMapping("/verify")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        log.info("━━━ VERIFY EMAIL ━━━");
        try {
            User user = emailVerificationService.verifyToken(token);
            notificationService.sendWelcome(user.getTenant(), user.getFullName());
            return ResponseEntity.ok(Map.of(
                    "message", "Email verified! Your account is now active. You can now log in.",
                    "email", user.getEmail()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(@RequestBody Map<String, String> req) {
        try {
            emailVerificationService.resendVerificationEmail(req.get("email"));
            return ResponseEntity.ok(Map.of("message", "Verification email resent. Please check your inbox."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(toUserDto(user));
    }

    private UserDto toUserDto(User user) {
        return new UserDto(
                user.getId(), user.getEmail(), user.getFullName(),
                user.getRole().name(), user.getTenant().getId(),
                user.getTenant().getName(), user.getTenant().getSlug(),
                user.getTenant().getPlan().name()
        );
    }
}
