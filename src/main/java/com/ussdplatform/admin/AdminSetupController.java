package com.ussdplatform.admin;

import com.ussdplatform.model.AdminUser;
import com.ussdplatform.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * One-time endpoint to seed the first admin account.
 * Automatically locks itself after first admin is created.
 * Can also be fully disabled via ADMIN_SETUP_ENABLED=false
 */
@RestController
@RequestMapping("/api/admin/setup")
@RequiredArgsConstructor
@Slf4j
public class AdminSetupController {

    private final AdminUserRepository adminUserRepo;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.setup-secret:CHANGE_ME_BEFORE_DEPLOY}")
    private String setupSecret;

    @Value("${admin.setup-enabled:true}")
    private boolean setupEnabled;

    @PostMapping
    public ResponseEntity<Map<String, String>> setup(
            @RequestHeader("X-Setup-Secret") String secret,
            @RequestBody Map<String, String> req) {

        // Disabled via config
        if (!setupEnabled) {
            log.warn("Admin setup attempted but endpoint is disabled");
            return ResponseEntity.status(404).body(Map.of("error", "Not found"));
        }

        // Locked after first admin exists
        if (adminUserRepo.count() > 0) {
            log.warn("Admin setup attempted but admin already exists — endpoint locked");
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Admin already configured. Use /api/admin/users to manage admins."));
        }

        if (!setupSecret.equals(secret)) {
            log.warn("Admin setup attempted with wrong secret");
            return ResponseEntity.status(401).body(Map.of("error", "Invalid setup secret"));
        }

        String email    = req.get("email");
        String password = req.get("password");
        String fullName = req.get("fullName");

        if (email == null || password == null || fullName == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "email, password, and fullName are required"));
        }
        if (password.length() < 8) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Password must be at least 8 characters"));
        }

        AdminUser admin = AdminUser.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .fullName(fullName)
                .isActive(true)
                .build();
        adminUserRepo.save(admin);
        log.info("First admin account created: {}", email);

        return ResponseEntity.ok(Map.of(
                "message", "Admin created. Endpoint now locked — add ADMIN_SETUP_ENABLED=false to your env to fully disable it.",
                "email", email
        ));
    }
}
