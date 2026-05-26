package com.ussdplatform.controller;

import com.ussdplatform.dto.*;
import com.ussdplatform.model.Tenant;
import com.ussdplatform.model.User;
import com.ussdplatform.notification.EmailVerificationService;
import com.ussdplatform.notification.NotificationService;
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

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("━━━ REGISTER ━━━ email={}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("  ✗ Email already registered: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new AuthResponse(null, null, "Email already registered"));
        }

        // Create tenant
        String slug = request.getCompanyName().toLowerCase()
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-");
        String finalSlug = slug;
        int attempt = 0;
        while (tenantRepository.existsBySlug(finalSlug)) {
            finalSlug = slug + "-" + (++attempt);
        }

        Tenant tenant = Tenant.builder()
                .name(request.getCompanyName())
                .slug(finalSlug)
                .email(request.getEmail())
                .phone(request.getPhone())
                .status(Tenant.TenantStatus.TRIAL)
                .plan(Tenant.Plan.FREE)
                .build();
        tenantRepository.save(tenant);
        log.info("  ✓ Tenant created: {} ({})", tenant.getName(), tenant.getId());

        // Create user with PENDING status — must verify email first
        User user = User.builder()
                .tenant(tenant)
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(User.Role.OWNER)
                .status(User.UserStatus.PENDING)
                .build();
        userRepository.save(user);
        log.info("  ✓ User created: {} (status=PENDING)", user.getId());

        // Send verification email (async)
        emailVerificationService.sendVerificationEmail(user);
        log.info("  ✓ Verification email queued for: {}", request.getEmail());

        // Return 201 but no token — user must verify first
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(null, null,
                        "Account created! Please check your email (" + request.getEmail() + ") and click the verification link to activate your account."));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("━━━ LOGIN ━━━ email={}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user == null) {
            log.warn("  ✗ No user found for: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, null, "Invalid email or password"));
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("  ✗ Wrong password for: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, null, "Invalid email or password"));
        }

        // Block login if email not verified
        if (user.getStatus() == User.UserStatus.PENDING) {
            log.warn("  ✗ Email not verified for: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new AuthResponse(null, null,
                            "Please verify your email before logging in. Check your inbox for the verification link."));
        }

        if (user.getStatus() == User.UserStatus.INACTIVE) {
            log.warn("  ✗ Account inactive for: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new AuthResponse(null, null, "Your account has been deactivated. Please contact support."));
        }

        String token = jwtService.generateToken(user);
        log.info("  ✓ Login successful for: {}", request.getEmail());
        return ResponseEntity.ok(new AuthResponse(token, toUserDto(user), null));
    }

    @GetMapping("/verify")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        log.info("━━━ VERIFY EMAIL ━━━ token={}...", token.substring(0, 8));
        try {
            User user = emailVerificationService.verifyToken(token);
            // Send welcome email after verification
            notificationService.sendWelcome(user.getTenant(), user.getFullName());
            log.info("  ✓ Account verified for: {}", user.getEmail());
            return ResponseEntity.ok(Map.of(
                    "message", "Email verified successfully! Your account is now active.",
                    "email", user.getEmail()
            ));
        } catch (IllegalArgumentException e) {
            log.warn("  ✗ Verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(@RequestBody Map<String, String> req) {
        String email = req.get("email");
        log.info("━━━ RESEND VERIFICATION ━━━ email={}", email);
        try {
            emailVerificationService.resendVerificationEmail(email);
            return ResponseEntity.ok(Map.of(
                    "message", "Verification email resent. Please check your inbox."
            ));
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
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getTenant().getId(),
                user.getTenant().getName(),
                user.getTenant().getSlug(),
                user.getTenant().getPlan().name()
        );
    }
}
