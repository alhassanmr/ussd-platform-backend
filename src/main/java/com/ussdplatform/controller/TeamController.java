package com.ussdplatform.controller;

import com.ussdplatform.model.*;
import com.ussdplatform.notification.TeamInviteService;
import com.ussdplatform.repository.*;
import com.ussdplatform.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/team")
@RequiredArgsConstructor
@Slf4j
public class TeamController {

    private final UserRepository userRepo;
    private final TeamInviteRepository inviteRepo;
    private final TeamInviteService inviteService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // ─── List team members ────────────────────────────────────────────────────

    @GetMapping("/members")
    public List<Map<String, Object>> listMembers(@AuthenticationPrincipal User currentUser) {
        return userRepo.findByTenantId(currentUser.getTenant().getId()).stream()
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", u.getId());
                    m.put("fullName", u.getFullName());
                    m.put("email", u.getEmail());
                    m.put("role", u.getRole().name());
                    m.put("status", u.getStatus().name());
                    m.put("createdAt", u.getCreatedAt());
                    m.put("isCurrentUser", u.getId().equals(currentUser.getId()));
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ─── List pending invites ─────────────────────────────────────────────────

    @GetMapping("/invites")
    public List<Map<String, Object>> listInvites(@AuthenticationPrincipal User currentUser) {
        return inviteRepo.findByTenantIdOrderByCreatedAtDesc(currentUser.getTenant().getId())
                .stream()
                .filter(i -> !i.isAccepted())
                .map(i -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", i.getId());
                    m.put("email", i.getEmail());
                    m.put("role", i.getRole().name());
                    m.put("invitedBy", i.getInvitedBy().getFullName());
                    m.put("expiresAt", i.getExpiresAt());
                    m.put("expired", i.isExpired());
                    m.put("createdAt", i.getCreatedAt());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ─── Send invite ──────────────────────────────────────────────────────────

    @PostMapping("/invite")
    public ResponseEntity<Map<String, Object>> sendInvite(
            @AuthenticationPrincipal User currentUser,
            @RequestBody Map<String, String> req) {

        // Only OWNER and ADMIN can invite
        if (currentUser.getRole() == User.Role.MEMBER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only owners and admins can invite team members"));
        }

        String email = req.get("email");
        String roleStr = req.getOrDefault("role", "MEMBER").toUpperCase();

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
        }

        // Can't invite yourself
        if (email.equalsIgnoreCase(currentUser.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "You cannot invite yourself"));
        }

        // Check if already a member
        if (userRepo.existsByEmail(email)) {
            User existing = userRepo.findByEmail(email).orElse(null);
            if (existing != null && existing.getTenant().getId().equals(currentUser.getTenant().getId())) {
                return ResponseEntity.badRequest().body(Map.of("error", email + " is already a team member"));
            }
        }

        User.Role role;
        try {
            role = User.Role.valueOf(roleStr);
            // Nobody can invite someone with higher role than themselves
            if (currentUser.getRole() == User.Role.ADMIN && role == User.Role.OWNER) {
                return ResponseEntity.badRequest().body(Map.of("error", "Admins cannot invite owners"));
            }
        } catch (IllegalArgumentException e) {
            role = User.Role.MEMBER;
        }

        try {
            inviteService.sendInvite(currentUser.getTenant(), currentUser, email, role);
            log.info("Invite sent to {} by {}", email, currentUser.getEmail());
            return ResponseEntity.ok(Map.of(
                    "message", "Invitation sent to " + email,
                    "email", email,
                    "role", role.name()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Revoke invite ────────────────────────────────────────────────────────

    @DeleteMapping("/invite/{id}")
    public ResponseEntity<?> revokeInvite(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id) {

        if (currentUser.getRole() == User.Role.MEMBER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Insufficient permissions"));
        }

        inviteRepo.findById(id).ifPresent(invite -> {
            if (invite.getTenant().getId().equals(currentUser.getTenant().getId())) {
                inviteRepo.delete(invite);
            }
        });
        return ResponseEntity.noContent().build();
    }

    // ─── Update member role ───────────────────────────────────────────────────

    @PutMapping("/members/{id}/role")
    public ResponseEntity<Map<String, Object>> updateRole(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id,
            @RequestBody Map<String, String> req) {

        if (currentUser.getRole() != User.Role.OWNER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only owners can change roles"));
        }

        return userRepo.findById(id)
                .filter(u -> u.getTenant().getId().equals(currentUser.getTenant().getId()))
                .map(u -> {
                    if (u.getId().equals(currentUser.getId())) {
                        return ResponseEntity.badRequest()
                                .<Map<String, Object>>body(Map.of("error", "You cannot change your own role"));
                    }
                    try {
                        u.setRole(User.Role.valueOf(req.get("role").toUpperCase()));
                        userRepo.save(u);
                        return ResponseEntity.ok(Map.of(
                                "id", u.getId(), "role", u.getRole().name()));
                    } catch (Exception e) {
                        return ResponseEntity.badRequest()
                                .<Map<String, Object>>body(Map.of("error", "Invalid role"));
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ─── Remove member ────────────────────────────────────────────────────────

    @DeleteMapping("/members/{id}")
    public ResponseEntity<?> removeMember(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id) {

        if (currentUser.getRole() != User.Role.OWNER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only owners can remove members"));
        }

        return userRepo.findById(id)
                .filter(u -> u.getTenant().getId().equals(currentUser.getTenant().getId()))
                .map(u -> {
                    if (u.getId().equals(currentUser.getId())) {
                        return ResponseEntity.badRequest()
                                .<Object>body(Map.of("error", "You cannot remove yourself"));
                    }
                    if (u.getRole() == User.Role.OWNER) {
                        return ResponseEntity.badRequest()
                                .<Object>body(Map.of("error", "Cannot remove the owner"));
                    }
                    userRepo.delete(u);
                    return ResponseEntity.noContent().<Object>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ─── Validate invite token (public) ──────────────────────────────────────

    @GetMapping("/invite/validate")
    public ResponseEntity<Map<String, Object>> validateInvite(@RequestParam String token) {
        try {
            TeamInvite invite = inviteService.validateInvite(token);
            return ResponseEntity.ok(Map.of(
                    "email", invite.getEmail(),
                    "role", invite.getRole().name(),
                    "tenantName", invite.getTenant().getName(),
                    "invitedBy", invite.getInvitedBy().getFullName()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Accept invite (public) ───────────────────────────────────────────────

    @PostMapping("/invite/accept")
    public ResponseEntity<Map<String, Object>> acceptInvite(@RequestBody Map<String, String> req) {
        String token    = req.get("token");
        String fullName = req.get("fullName");
        String password = req.get("password");
        String phone    = req.get("phone");

        if (token == null || fullName == null || password == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "token, fullName and password are required"));
        }
        if (password.length() < 6) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Password must be at least 6 characters"));
        }

        try {
            TeamInvite invite = inviteService.validateInvite(token);

            // Create the user
            User user = User.builder()
                    .tenant(invite.getTenant())
                    .email(invite.getEmail())
                    .password(passwordEncoder.encode(password))
                    .fullName(fullName)
                    .phone(phone)
                    .role(invite.getRole())
                    .status(User.UserStatus.ACTIVE) // invite = already verified email
                    .build();
            userRepo.save(user);

            inviteService.markAccepted(invite);

            String jwtToken = jwtService.generateToken(user);
            log.info("Invite accepted by {} for tenant {}", user.getEmail(), invite.getTenant().getName());

            return ResponseEntity.ok(Map.of(
                    "token", jwtToken,
                    "message", "Welcome to " + invite.getTenant().getName() + "!"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
