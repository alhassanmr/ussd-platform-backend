package com.ussdplatform.controller;

import com.ussdplatform.engine.UssdEngine;
import com.ussdplatform.gateway.UssdRequest;
import com.ussdplatform.gateway.UssdResponse;
import com.ussdplatform.model.User;
import com.ussdplatform.model.UssdApp;
import com.ussdplatform.repository.UssdAppRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
@Slf4j
public class SimulatorController {

    private final UssdEngine engine;
    private final UssdAppRepository appRepo;

    @PostMapping("/{appId}")
    public ResponseEntity<Map<String, Object>> simulate(
            @AuthenticationPrincipal User user,
            @PathVariable UUID appId,
            @RequestBody Map<String, String> req) {

        UssdApp app = appRepo.findByIdAndTenantId(appId, user.getTenant().getId())
                .orElse(null);
        if (app == null) return ResponseEntity.notFound().build();

        // SIM- prefix marks this as a simulator session, excluded from analytics
        String sessionId = "SIM-" + appId + "-" + req.getOrDefault("sessionId", UUID.randomUUID().toString());
        String input     = req.getOrDefault("input", "");
        boolean isNew    = "true".equals(req.get("isNew")) || input.isEmpty();
        String msisdn    = req.getOrDefault("msisdn", "+233000000000");

        // Temporarily set app to ACTIVE for simulation
        UssdApp.AppStatus originalStatus = app.getStatus();
        boolean wasNotActive = app.getStatus() != UssdApp.AppStatus.ACTIVE;
        if (wasNotActive) app.setStatus(UssdApp.AppStatus.ACTIVE);

        try {
            UssdRequest ussdReq = UssdRequest.builder()
                    .sessionId(sessionId)
                    .msisdn(msisdn)
                    .shortCode(app.getShortCode() != null ? app.getShortCode() : "*000#")
                    .input(input)
                    .isNew(isNew)
                    .build();

            UssdResponse response = engine.process(app, ussdReq);
            log.info("Simulator: app={} input='{}' -> '{}'", appId, input,
                    response.getMessage().substring(0, Math.min(50, response.getMessage().length())));

            return ResponseEntity.ok(Map.of(
                    "message",        response.getMessage(),
                    "shouldContinue", response.isShouldContinue(),
                    "sessionId",      sessionId
            ));
        } finally {
            if (wasNotActive) app.setStatus(originalStatus);
        }
    }

    @DeleteMapping("/{appId}/session")
    public ResponseEntity<Void> clearSession(
            @AuthenticationPrincipal User user,
            @PathVariable UUID appId,
            @RequestParam String sessionId) {
        // Session will expire naturally via Redis TTL
        // This endpoint just confirms the reset on the frontend
        log.info("Simulator session cleared: {}", sessionId);
        return ResponseEntity.noContent().build();
    }
}
