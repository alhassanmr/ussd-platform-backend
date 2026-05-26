package com.ussdplatform.controller;

import com.ussdplatform.dto.*;
import com.ussdplatform.model.Tenant;
import com.ussdplatform.model.User;
import com.ussdplatform.repository.TenantRepository;
import com.ussdplatform.repository.UserRepository;
import com.ussdplatform.notification.NotificationService;
import com.ussdplatform.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("━━━ REGISTER ━━━");
        log.info("  Email      : {}", request.getEmail());
        log.info("  Full name  : {}", request.getFullName());
        log.info("  Company    : {}", request.getCompanyName());

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("  ✗ Email already registered: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new AuthResponse(null, null, "Email already registered"));
        }

        String slug = request.getCompanyName().toLowerCase()
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-");
        String finalSlug = slug;
        int attempt = 0;
        while (tenantRepository.existsBySlug(finalSlug)) {
            finalSlug = slug + "-" + (++attempt);
        }
        log.info("  Slug       : {}", finalSlug);

        Tenant tenant = Tenant.builder()
                .name(request.getCompanyName())
                .slug(finalSlug)
                .email(request.getEmail())
                .phone(request.getPhone())
                .status(Tenant.TenantStatus.TRIAL)
                .plan(Tenant.Plan.FREE)
                .build();
        tenantRepository.save(tenant);
        log.info("  ✓ Tenant created: id={}", tenant.getId());

        User user = User.builder()
                .tenant(tenant)
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(User.Role.OWNER)
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepository.save(user);
        log.info("  ✓ User created: id={} status={}", user.getId(), user.getStatus());

        String token = jwtService.generateToken(user);
        // Send welcome email (async - won't block response)
        notificationService.sendWelcome(tenant, request.getFullName());
        log.info("  ✓ Welcome email queued for: {}", request.getEmail());
        log.info("  ✓ Token generated, returning 201");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(token, toUserDto(user), null));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("━━━ LOGIN ━━━");
        log.info("  Email: {}", request.getEmail());
        log.info("  Password provided: {}", request.getPassword() != null && !request.getPassword().isEmpty() ? "yes (length=" + request.getPassword().length() + ")" : "NO - empty!");

        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user == null) {
            log.warn("  ✗ No user found for email: {}", request.getEmail());
            log.warn("  Tip: call /api/auth/register first, or check the email spelling");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, null, "Invalid credentials"));
        }

        log.info("  ✓ User found: id={} status={} role={}", user.getId(), user.getStatus(), user.getRole());
        log.info("  Stored password hash: {}...", user.getPassword().substring(0, 20));

        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPassword());
        log.info("  Password matches: {}", passwordMatches ? "✓ YES" : "✗ NO");

        if (!passwordMatches) {
            log.warn("  ✗ Wrong password for: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, null, "Invalid credentials"));
        }

        if (user.getStatus() != User.UserStatus.ACTIVE) {
            log.warn("  ✗ Account not active: {} (status={})", request.getEmail(), user.getStatus());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new AuthResponse(null, null, "Account is inactive"));
        }

        String token = jwtService.generateToken(user);
        log.info("  ✓ Login successful for: {}", request.getEmail());
        log.info("  ✓ Token generated, returning 200");
        return ResponseEntity.ok(new AuthResponse(token, toUserDto(user), null));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal User user) {
        log.info("━━━ ME ━━━ user={}", user != null ? user.getEmail() : "null (not authenticated)");
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
