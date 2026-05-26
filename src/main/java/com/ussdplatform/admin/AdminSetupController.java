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
 *
 * Protected by a setup secret defined in application.yml.
 * Once an admin exists, this endpoint returns 403.
 *
 * Usage:
 *   POST /api/admin/setup
 *   Header: X-Setup-Secret: your_setup_secret
 *   Body: { "email": "you@example.com", "password": "strong_password", "fullName": "Your Name" }
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

    @PostMapping
    public ResponseEntity<Map<String, String>> setup(
            @RequestHeader("X-Setup-Secret") String secret,
            @RequestBody Map<String, String> req) {

        // Only allow if no admins exist yet
        if (adminUserRepo.count() > 0) {
            log.warn("Admin setup attempted but admin already exists");
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Admin already configured. Use the login endpoint."));
        }

        // Verify setup secret
        if (!setupSecret.equals(secret)) {
            log.warn("Admin setup attempted with wrong secret");
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid setup secret"));
        }

        String email = req.get("email");
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
        log.info("First admin account created for: {}", email);

        return ResponseEntity.ok(Map.of(
                "message", "Admin account created successfully. You can now log in at /admin",
                "email", email
        ));
    }
}
