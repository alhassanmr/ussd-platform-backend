package com.ussdplatform.controller;

import com.ussdplatform.dto.*;
import com.ussdplatform.model.Tenant;
import com.ussdplatform.model.User;
import com.ussdplatform.repository.TenantRepository;
import com.ussdplatform.repository.UserRepository;
import com.ussdplatform.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
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

        Tenant tenant = Tenant.builder()
                .name(request.getCompanyName())
                .slug(finalSlug)
                .email(request.getEmail())
                .phone(request.getPhone())
                .status(Tenant.TenantStatus.TRIAL)
                .plan(Tenant.Plan.FREE)
                .build();
        tenantRepository.save(tenant);

        User user = User.builder()
                .tenant(tenant)
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(User.Role.OWNER)
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        String token = jwtService.generateToken(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(token, toUserDto(user), null));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, null, "Invalid credentials"));
        }

        if (user.getStatus() != User.UserStatus.ACTIVE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new AuthResponse(null, null, "Account is inactive"));
        }

        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(new AuthResponse(token, toUserDto(user), null));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal User user) {
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
