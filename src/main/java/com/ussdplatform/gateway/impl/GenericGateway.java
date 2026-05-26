package com.ussdplatform.gateway.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ussdplatform.gateway.UssdGateway;
import com.ussdplatform.gateway.UssdRequest;
import com.ussdplatform.gateway.UssdResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Generic/Custom USSD Gateway Adapter
 *
 * For any gateway not explicitly supported.
 * Accepts a flexible JSON format that maps common field names.
 *
 * Supported input field names (tries each in order):
 *   sessionId: session_id, sessionId, SessionId, id
 *   msisdn:    msisdn, phone, phone_number, phoneNumber, mobile, Mobile
 *   shortCode: service_code, serviceCode, ServiceCode, short_code, shortCode
 *   input:     text, input, userdata, ussd_string, message, Message
 *   isNew:     new_session, isNew, type=initiation, text=""
 *
 * Response format: CON/END plain text (most widely supported)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GenericGateway implements UssdGateway {

    private final ObjectMapper objectMapper;

    @Override
    @SuppressWarnings("unchecked")
    public UssdRequest parseRequest(String rawBody) {
        try {
            // Try JSON first
            if (rawBody.trim().startsWith("{")) {
                Map<String, Object> body = objectMapper.readValue(rawBody, Map.class);
                return parseJson(body);
            }
            // Fall back to form-encoded (like Africa's Talking)
            return parseFormEncoded(rawBody);
        } catch (Exception e) {
            log.error("Failed to parse Generic/Custom request", e);
            throw new RuntimeException("Invalid gateway request format. Supported: JSON or form-encoded.", e);
        }
    }

    @SuppressWarnings("unchecked")
    private UssdRequest parseJson(Map<String, Object> body) {
        String sessionId  = getFirst(body, "session_id", "sessionId", "SessionId", "id");
        String msisdn     = getFirst(body, "msisdn", "phone", "phone_number", "phoneNumber", "mobile", "Mobile");
        String shortCode  = getFirst(body, "service_code", "serviceCode", "ServiceCode", "short_code", "shortCode");
        String inputText  = getFirst(body, "text", "input", "userdata", "ussd_string", "message", "Message", "userInput");
        String typeField  = getFirst(body, "type", "Type", "session_type");

        boolean isNew = "initiation".equalsIgnoreCase(typeField)
                || "Initiation".equals(typeField)
                || Boolean.TRUE.equals(body.get("new_session"))
                || Boolean.TRUE.equals(body.get("isNew"))
                || (inputText == null || inputText.isEmpty());

        // Normalise msisdn
        if (msisdn != null && !msisdn.startsWith("+") && msisdn.matches("\\d+")) {
            msisdn = "+" + msisdn;
        }

        // Extract last segment if cumulative (contains *)
        String currentInput = isNew ? "" : (inputText != null && inputText.contains("*")
                ? inputText.substring(inputText.lastIndexOf('*') + 1)
                : (inputText != null ? inputText : ""));

        log.debug("Generic gateway parsed: session={} msisdn={} input='{}' isNew={}",
                sessionId, msisdn, currentInput, isNew);

        return UssdRequest.builder()
                .sessionId(sessionId)
                .msisdn(msisdn)
                .shortCode(shortCode)
                .input(currentInput)
                .isNew(isNew)
                .build();
    }

    private UssdRequest parseFormEncoded(String rawBody) {
        java.util.HashMap<String, String> params = new java.util.HashMap<>();
        for (String pair : rawBody.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                try {
                    params.put(
                        java.net.URLDecoder.decode(parts[0], java.nio.charset.StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8)
                    );
                } catch (Exception ignored) {}
            }
        }

        String text   = params.getOrDefault("text", params.getOrDefault("input", ""));
        boolean isNew = text.isEmpty();
        String input  = isNew ? "" : (text.contains("*") ? text.substring(text.lastIndexOf('*') + 1) : text);

        return UssdRequest.builder()
                .sessionId(params.getOrDefault("sessionId", params.getOrDefault("session_id", "")))
                .msisdn(params.getOrDefault("phoneNumber", params.getOrDefault("msisdn", params.getOrDefault("phone", ""))))
                .shortCode(params.getOrDefault("serviceCode", params.getOrDefault("service_code", "")))
                .input(input)
                .isNew(isNew)
                .build();
    }

    @Override
    public String formatResponse(UssdResponse response) {
        // CON/END is most widely supported format
        return (response.isShouldContinue() ? "CON " : "END ") + response.getMessage();
    }

    @Override
    public String getGatewayType() {
        return "CUSTOM";
    }

    private String getFirst(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val != null) return val.toString();
        }
        return null;
    }
}
