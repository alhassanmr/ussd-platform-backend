package com.ussdplatform.gateway.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ussdplatform.gateway.UssdGateway;
import com.ussdplatform.gateway.UssdRequest;
import com.ussdplatform.gateway.UssdResponse;
import com.ussdplatform.model.UssdApp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Configurable Custom Gateway — reads field mappings from the app's gatewayConfig.
 *
 * Tenants configure this via the UI by specifying:
 *
 * Request field mappings:
 *   req.sessionId   = field name in incoming request (e.g. "session_id")
 *   req.msisdn      = field name for phone number (e.g. "phone")
 *   req.shortCode   = field name for short code (e.g. "service_code")
 *   req.input       = field name for user input (e.g. "text")
 *   req.isNew       = field name for new session flag (e.g. "new_session")
 *   req.isNewValue  = value that means "new session" (e.g. "true" or "1" or "initiation")
 *   req.format      = "json" | "form" (default: json)
 *   req.cumulative  = "true" if input is cumulative 1*2*3 format (default: false)
 *
 * Response format:
 *   res.format      = "json" | "text" (default: text CON/END)
 *   res.message     = field name for message in JSON response (e.g. "msg")
 *   res.continue    = field name for continue flag (e.g. "should_continue")
 *   res.continueVal = value that means continue (e.g. "true" or "1" or "CONTINUE")
 *   res.endVal      = value that means end (e.g. "false" or "0" or "END")
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConfigurableGateway implements UssdGateway {

    private final ObjectMapper objectMapper;

    @Override
    public UssdRequest parseRequest(String rawBody) {
        // This method is called without config - use GenericGateway behaviour
        throw new UnsupportedOperationException(
            "ConfigurableGateway requires app config. Use parseRequest(String, UssdApp) instead.");
    }

    /**
     * Parse with app-specific config.
     */
    @SuppressWarnings("unchecked")
    public UssdRequest parseRequest(String rawBody, UssdApp app) {
        Map<String, String> cfg = app.getGatewayConfig();
        if (cfg == null || cfg.isEmpty()) {
            // Fall back to generic parsing
            return parseGeneric(rawBody);
        }

        try {
            String format = cfg.getOrDefault("req.format", "json").toLowerCase();

            Map<String, Object> body;
            if ("form".equals(format)) {
                body = parseFormToMap(rawBody);
            } else {
                body = objectMapper.readValue(rawBody, Map.class);
            }

            String sessionIdField  = cfg.getOrDefault("req.sessionId",  "sessionId");
            String msisdnField     = cfg.getOrDefault("req.msisdn",     "msisdn");
            String shortCodeField  = cfg.getOrDefault("req.shortCode",  "serviceCode");
            String inputField      = cfg.getOrDefault("req.input",      "text");
            String isNewField      = cfg.getOrDefault("req.isNew",      "");
            String isNewValue      = cfg.getOrDefault("req.isNewValue", "true");
            boolean cumulative     = "true".equalsIgnoreCase(cfg.getOrDefault("req.cumulative", "false"));

            String sessionId = getString(body, sessionIdField);
            String msisdn    = getString(body, msisdnField);
            String shortCode = getString(body, shortCodeField);
            String rawInput  = getString(body, inputField);

            // Determine if new session
            boolean isNew;
            if (!isNewField.isEmpty()) {
                String val = getString(body, isNewField);
                isNew = isNewValue.equalsIgnoreCase(val);
            } else {
                isNew = rawInput == null || rawInput.isEmpty();
            }

            // Normalise msisdn
            if (msisdn != null && !msisdn.startsWith("+") && msisdn != null && msisdn.matches("\\d+")) {
                msisdn = "+" + msisdn;
            }

            // Handle cumulative input
            String input = isNew ? "" : (cumulative && rawInput != null && rawInput.contains("*")
                    ? rawInput.substring(rawInput.lastIndexOf('*') + 1)
                    : (rawInput != null ? rawInput : ""));

            log.debug("Configurable gateway: session={} msisdn={} isNew={} input='{}'",
                    sessionId, msisdn, isNew, input);

            return UssdRequest.builder()
                    .sessionId(sessionId)
                    .msisdn(msisdn)
                    .shortCode(shortCode)
                    .input(input)
                    .isNew(isNew)
                    .build();

        } catch (Exception e) {
            log.error("Configurable gateway parse failed, falling back to generic", e);
            return parseGeneric(rawBody);
        }
    }

    /**
     * Format response with app-specific config.
     */
    public String formatResponse(UssdResponse response, UssdApp app) {
        Map<String, String> cfg = app.getGatewayConfig();
        if (cfg == null || cfg.isEmpty()) {
            return (response.isShouldContinue() ? "CON " : "END ") + response.getMessage();
        }

        String format       = cfg.getOrDefault("res.format", "text").toLowerCase();
        String msgField     = cfg.getOrDefault("res.message",     "message");
        String continueField = cfg.getOrDefault("res.continue",   "shouldContinue");
        String continueVal  = cfg.getOrDefault("res.continueVal", "true");
        String endVal       = cfg.getOrDefault("res.endVal",      "false");

        if ("json".equals(format)) {
            try {
                Map<String, Object> json = Map.of(
                        msgField,      response.getMessage(),
                        continueField, response.isShouldContinue() ? continueVal : endVal
                );
                return objectMapper.writeValueAsString(json);
            } catch (Exception e) {
                log.error("Failed to format configurable JSON response", e);
            }
        }

        // Default: CON/END text
        return (response.isShouldContinue() ? "CON " : "END ") + response.getMessage();
    }

    @Override
    public String formatResponse(UssdResponse response) {
        return (response.isShouldContinue() ? "CON " : "END ") + response.getMessage();
    }

    @Override
    public String getGatewayType() {
        return "CONFIGURABLE";
    }

    @SuppressWarnings("unchecked")
    private UssdRequest parseGeneric(String rawBody) {
        try {
            Map<String, Object> body = rawBody.trim().startsWith("{")
                    ? objectMapper.readValue(rawBody, Map.class)
                    : parseFormToMap(rawBody);

            String text   = getFirstMatch(body, "text", "input", "userdata", "ussd_string", "USERDATA", "MSG");
            boolean isNew = text == null || text.isEmpty();
            String input  = isNew ? "" : (text.contains("*") ? text.substring(text.lastIndexOf('*') + 1) : text);

            return UssdRequest.builder()
                    .sessionId(getFirstMatch(body, "sessionId", "session_id", "SESSIONID"))
                    .msisdn(getFirstMatch(body, "msisdn", "phone", "phone_number", "MSISDN", "USERID"))
                    .shortCode(getFirstMatch(body, "serviceCode", "service_code", "shortCode", "SHORTCODE"))
                    .input(input)
                    .isNew(isNew)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse custom gateway request", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFormToMap(String body) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                try {
                    map.put(
                        java.net.URLDecoder.decode(parts[0], java.nio.charset.StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8)
                    );
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private String getFirstMatch(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val != null) return val.toString();
        }
        return null;
    }
}
