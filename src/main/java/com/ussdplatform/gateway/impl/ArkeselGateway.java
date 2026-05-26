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
 * Arkesel USSD Gateway Adapter (Ghana)
 *
 * Arkesel sends JSON POST:
 * {
 *   "session_id": "...",
 *   "phone_number": "+233244000001",
 *   "service_code": "*714#",
 *   "text": "cumulative input separated by *",
 *   "network_code": "MTN"
 * }
 *
 * Arkesel expects plain text response:
 *   "CON menu text"  — continue session
 *   "END end text"   — terminate session
 *
 * Note: Arkesel uses the same CON/END format as Africa's Talking
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ArkeselGateway implements UssdGateway {

    private final ObjectMapper objectMapper;

    @Override
    @SuppressWarnings("unchecked")
    public UssdRequest parseRequest(String rawBody) {
        try {
            Map<String, Object> body = objectMapper.readValue(rawBody, Map.class);
            String text   = (String) body.getOrDefault("text", "");
            boolean isNew = text == null || text.isEmpty();

            // Arkesel sends cumulative input like Africa's Talking
            String currentInput = isNew ? "" : (text.contains("*")
                    ? text.substring(text.lastIndexOf('*') + 1)
                    : text);

            return UssdRequest.builder()
                    .sessionId((String) body.get("session_id"))
                    .msisdn((String) body.get("phone_number"))
                    .shortCode((String) body.get("service_code"))
                    .input(currentInput)
                    .isNew(isNew)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse Arkesel request", e);
            throw new RuntimeException("Invalid Arkesel request", e);
        }
    }

    @Override
    public String formatResponse(UssdResponse response) {
        // Same CON/END format as Africa's Talking
        return (response.isShouldContinue() ? "CON " : "END ") + response.getMessage();
    }

    @Override
    public String getGatewayType() {
        return "ARKESEL";
    }
}
