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
 * Hubtel USSD Gateway Adapter.
 *
 * Hubtel sends a JSON POST:
 * {
 *   "SessionId": "...",
 *   "Mobile": "233XXXXXXXXX",
 *   "ServiceCode": "*714#",
 *   "Type": "Initiation" | "Response" | "Release" | "Timeout",
 *   "Message": "user input"
 * }
 *
 * Hubtel expects JSON response:
 * {
 *   "SessionId": "...",
 *   "Type": "Response" | "Release",
 *   "Message": "text to show"
 * }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HubtelGateway implements UssdGateway {

    private final ObjectMapper objectMapper;

    @Override
    @SuppressWarnings("unchecked")
    public UssdRequest parseRequest(String rawBody) {
        try {
            Map<String, Object> body = objectMapper.readValue(rawBody, Map.class);

            String type = (String) body.getOrDefault("Type", "Response");
            boolean isNew = "Initiation".equalsIgnoreCase(type);

            return UssdRequest.builder()
                    .sessionId((String) body.get("SessionId"))
                    .msisdn((String) body.get("Mobile"))
                    .shortCode((String) body.get("ServiceCode"))
                    .input(isNew ? "" : (String) body.getOrDefault("Message", ""))
                    .isNew(isNew)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse Hubtel request", e);
            throw new RuntimeException("Invalid Hubtel request", e);
        }
    }

    @Override
    public String formatResponse(UssdResponse response) {
        try {
            Map<String, String> json = Map.of(
                    "Type", response.isShouldContinue() ? "Response" : "Release",
                    "Message", response.getMessage()
            );
            return objectMapper.writeValueAsString(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to format Hubtel response", e);
        }
    }

    @Override
    public String getGatewayType() {
        return "HUBTEL";
    }
}
