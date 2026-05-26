package com.ussdplatform.gateway.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ussdplatform.gateway.UssdGateway;
import com.ussdplatform.gateway.UssdRequest;
import com.ussdplatform.gateway.UssdResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Wigal USSD Gateway Adapter (Ghana)
 *
 * Wigal sends JSON POST:
 * {
 *   "msisdn": "233244000001",
 *   "sessionId": "...",
 *   "serviceCode": "*714#",
 *   "type": "initiation" | "response" | "termination",
 *   "userdata": "user input text"
 * }
 *
 * Wigal expects JSON response:
 * {
 *   "message": "text to show",
 *   "type": "response" | "release"
 * }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WigalGateway implements UssdGateway {

    private final ObjectMapper objectMapper;

    @Override
    @SuppressWarnings("unchecked")
    public UssdRequest parseRequest(String rawBody) {
        try {
            Map<String, Object> body = objectMapper.readValue(rawBody, Map.class);
            String type  = (String) body.getOrDefault("type", "response");
            boolean isNew = "initiation".equalsIgnoreCase(type);
            String msisdn = (String) body.getOrDefault("msisdn", "");

            // Wigal sends msisdn without + prefix — normalise to E.164
            if (msisdn != null && !msisdn.startsWith("+")) {
                msisdn = "+" + msisdn;
            }

            return UssdRequest.builder()
                    .sessionId((String) body.get("sessionId"))
                    .msisdn(msisdn)
                    .shortCode((String) body.get("serviceCode"))
                    .input(isNew ? "" : (String) body.getOrDefault("userdata", ""))
                    .isNew(isNew)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse Wigal request", e);
            throw new RuntimeException("Invalid Wigal request", e);
        }
    }

    @Override
    public String formatResponse(UssdResponse response) {
        try {
            Map<String, String> json = Map.of(
                    "message", response.getMessage(),
                    "type", response.isShouldContinue() ? "response" : "release"
            );
            return objectMapper.writeValueAsString(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to format Wigal response", e);
        }
    }

    @Override
    public String getGatewayType() {
        return "WIGAL";
    }
}
