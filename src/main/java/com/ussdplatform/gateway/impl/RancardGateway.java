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
 * Rancard USSD Gateway Adapter (Ghana)
 *
 * Rancard sends JSON POST:
 * {
 *   "msisdn": "233244000001",
 *   "session_id": "...",
 *   "ussd_string": "user input",
 *   "service_code": "*714#",
 *   "new_session": true | false,
 *   "network": "MTN" | "VODAFONE" | "AIRTELTIGO"
 * }
 *
 * Rancard expects JSON response:
 * {
 *   "message": "text to display",
 *   "continue_session": true | false
 * }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RancardGateway implements UssdGateway {

    private final ObjectMapper objectMapper;

    @Override
    @SuppressWarnings("unchecked")
    public UssdRequest parseRequest(String rawBody) {
        try {
            Map<String, Object> body = objectMapper.readValue(rawBody, Map.class);

            boolean isNew = Boolean.TRUE.equals(body.get("new_session"));
            String msisdn = (String) body.getOrDefault("msisdn", "");

            // Normalise msisdn to E.164
            if (msisdn != null && !msisdn.startsWith("+")) {
                msisdn = "+" + msisdn;
            }

            return UssdRequest.builder()
                    .sessionId((String) body.get("session_id"))
                    .msisdn(msisdn)
                    .shortCode((String) body.get("service_code"))
                    .input(isNew ? "" : (String) body.getOrDefault("ussd_string", ""))
                    .isNew(isNew)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse Rancard request", e);
            throw new RuntimeException("Invalid Rancard request", e);
        }
    }

    @Override
    public String formatResponse(UssdResponse response) {
        try {
            Map<String, Object> json = Map.of(
                    "message", response.getMessage(),
                    "continue_session", response.isShouldContinue()
            );
            return objectMapper.writeValueAsString(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to format Rancard response", e);
        }
    }

    @Override
    public String getGatewayType() {
        return "RANCARD";
    }
}
