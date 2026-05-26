package com.ussdplatform.controller;

import com.ussdplatform.engine.UssdEngine;
import com.ussdplatform.gateway.GatewayFactory;
import com.ussdplatform.gateway.UssdGateway;
import com.ussdplatform.gateway.UssdRequest;
import com.ussdplatform.gateway.UssdResponse;
import com.ussdplatform.model.UssdApp;
import com.ussdplatform.repository.UssdAppRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Entry point for all USSD gateway callbacks.
 *
 * Each app has its own webhook URL:
 *   POST /ussd/webhook/{appId}
 *
 * The gateway (AT, Hubtel, etc.) is detected from the app config.
 */
@RestController
@RequestMapping("/ussd/webhook")
@RequiredArgsConstructor
@Slf4j
public class UssdWebhookController {

    private final UssdEngine engine;
    private final GatewayFactory gatewayFactory;
    private final UssdAppRepository appRepository;

    /**
     * Africa's Talking sends form-encoded POST.
     * Hubtel sends JSON POST.
     * We accept both via plain String body and let the gateway adapter parse.
     */
    @PostMapping(value = "/{appId}",
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                        MediaType.APPLICATION_JSON_VALUE,
                        MediaType.TEXT_PLAIN_VALUE,
                        MediaType.ALL_VALUE})
    public ResponseEntity<String> handleUssd(
            @PathVariable UUID appId,
            @RequestBody String rawBody) {

        log.debug("USSD webhook received for app={}", appId);

        UssdApp app = appRepository.findById(appId)
                .orElse(null);

        if (app == null || app.getStatus() != UssdApp.AppStatus.ACTIVE) {
            log.warn("App {} not found or not active", appId);
            return ResponseEntity.ok("END Service unavailable.");
        }

        UssdGateway gateway = gatewayFactory.getGateway(app.getGatewayType());
        UssdRequest request;

        try {
            request = gateway.parseRequest(rawBody);
        } catch (Exception e) {
            log.error("Failed to parse gateway request for app {}", appId, e);
            return ResponseEntity.ok(gateway.formatResponse(
                    UssdResponse.builder()
                            .message("Service error. Please try again.")
                            .shouldContinue(false)
                            .build()));
        }

        UssdResponse response = engine.process(app, request);
        String formatted = gateway.formatResponse(response);
        log.debug("USSD response for session {}: {}", request.getSessionId(), formatted);

        return ResponseEntity.ok(formatted);
    }
}
