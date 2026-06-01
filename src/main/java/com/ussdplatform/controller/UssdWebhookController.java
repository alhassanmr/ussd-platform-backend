package com.ussdplatform.controller;

import com.ussdplatform.billing.UsageTrackingService;
import com.ussdplatform.gateway.impl.ConfigurableGateway;
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

import java.util.Map;
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
    private final UsageTrackingService usageTrackingService;
    private final ConfigurableGateway configurableGateway;

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

        if (app == null || app.isDeleted()) {
            log.warn("App {} not found or has been archived", appId);
            return ResponseEntity.ok("END This service is no longer available.");
        }

        if (app.getStatus() == UssdApp.AppStatus.DRAFT) {
            log.warn("App {} is in DRAFT — not accepting live traffic", appId);
            return ResponseEntity.ok("END This service is not yet live. Please try again later.");
        }

        if (app.getStatus() == UssdApp.AppStatus.PAUSED) {
            log.warn("App {} is PAUSED", appId);
            return ResponseEntity.ok("END This service is temporarily unavailable. Please try again later.");
        }

        UssdGateway gateway = gatewayFactory.getGateway(app.getGatewayType());
        UssdRequest request;

        // Normalise body based on request format setting
        String format = app.getRequestFormat() != null ? app.getRequestFormat().toUpperCase() : "JSON";
        String normalisedBody = rawBody;

        if ("XML".equals(format) && rawBody != null && rawBody.trim().startsWith("<")) {
            try {
                normalisedBody = xmlToJson(rawBody);
                log.debug("Converted XML body to JSON for processing");
            } catch (Exception e) {
                log.warn("Failed to convert XML body: {}", e.getMessage());
            }
        } else if ("FORM".equals(format) && rawBody != null && !rawBody.trim().startsWith("{")) {
            // Form-encoded — leave as-is, gateways handle it
            log.debug("Request format: FORM (url-encoded)");
        }

        try {
            // For CUSTOM/CONFIGURABLE apps use field mapping from app config
            if (app.getGatewayType() == com.ussdplatform.model.UssdApp.GatewayType.CUSTOM
                    || app.getGatewayType() == com.ussdplatform.model.UssdApp.GatewayType.CONFIGURABLE) {
                request = configurableGateway.parseRequest(normalisedBody, app);
            } else {
                request = gateway.parseRequest(normalisedBody);
            }
        } catch (Exception e) {
            log.error("Failed to parse gateway request for app {}", appId, e);
            return ResponseEntity.ok(gateway.formatResponse(
                    UssdResponse.builder()
                            .message("Service error. Please try again.")
                            .shouldContinue(false)
                            .build()));
        }

        UssdResponse response = engine.process(app, request);
        // Track usage asynchronously
        try { usageTrackingService.recordSession(app.getTenant(), app); } catch (Exception e) { log.warn("Usage tracking failed: {}", e.getMessage()); }
        String formatted;
        if (app.getGatewayType() == com.ussdplatform.model.UssdApp.GatewayType.CUSTOM
                || app.getGatewayType() == com.ussdplatform.model.UssdApp.GatewayType.CONFIGURABLE) {
            formatted = configurableGateway.formatResponse(response, app);
        } else {
            formatted = gateway.formatResponse(response);
        }
        log.debug("USSD response for session {}: {}", request.getSessionId(), formatted);

        return ResponseEntity.ok(formatted);
    }
    /**
     * Convert simple XML to flat JSON string for gateway parsing.
     * Handles flat XML like: <request><msisdn>233...</msisdn><text>1</text></request>
     */
    /**
     * Convert simple flat XML to JSON string for gateway parsing.
     */
    private String xmlToJson(String xml) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory factory =
                javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
        org.w3c.dom.Document doc = builder.parse(
                new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        doc.getDocumentElement().normalize();

        org.w3c.dom.NodeList nodes = doc.getDocumentElement().getChildNodes();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        java.util.Map<String,String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            org.w3c.dom.Node node = nodes.item(i);
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                map.put(node.getNodeName(), node.getTextContent());
            }
        }
        String result = mapper.writeValueAsString(map);
        log.debug("XML converted to JSON: {}", result);
        return result;
    }
}
