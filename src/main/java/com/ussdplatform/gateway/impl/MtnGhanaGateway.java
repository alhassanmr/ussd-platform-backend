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
 * MTN Ghana Direct USSD Gateway Adapter
 *
 * MTN Ghana direct connection is for enterprise clients with
 * a formal partnership agreement with MTN Ghana.
 * Requires: MTN Ghana Developer Portal account + approved short code.
 *
 * MTN Ghana API sends JSON POST:
 * {
 *   "requestId":    "unique request ID",
 *   "msisdn":       "233244000001",
 *   "ussdString":   "user input",
 *   "serviceCode":  "*714#",
 *   "sessionId":    "unique session ID",
 *   "sessionStatus": "NEW" | "CONTINUING" | "ENDING",
 *   "network":      "MTN"
 * }
 *
 * MTN Ghana expects JSON response:
 * {
 *   "requestId":   "echo request ID",
 *   "responseString": "text to display",
 *   "action":      "INPUT" (continue) | "BREAK" (end session)
 * }
 *
 * Note: Requires MTN Ghana enterprise partnership.
 * Contact: developer.mtn.com.gh or MTN Business Ghana.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MtnGhanaGateway implements UssdGateway {

    private final ObjectMapper objectMapper;
    // Store last requestId per session for echoing back in response
    private final java.util.concurrent.ConcurrentHashMap<String, String> requestIdMap
            = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public UssdRequest parseRequest(String rawBody) {
        try {
            Map<String, Object> body = objectMapper.readValue(rawBody, Map.class);

            String sessionId  = (String) body.get("sessionId");
            String requestId  = (String) body.get("requestId");
            String status     = (String) body.getOrDefault("sessionStatus", "CONTINUING");
            boolean isNew     = "NEW".equalsIgnoreCase(status);

            // Store requestId for response
            if (requestId != null && sessionId != null) {
                requestIdMap.put(sessionId, requestId);
            }

            String msisdn = (String) body.getOrDefault("msisdn", "");
            if (msisdn != null && !msisdn.startsWith("+")) {
                msisdn = "+" + msisdn;
            }

            log.debug("MTN Ghana: session={} status={} input='{}'",
                    sessionId, status, body.get("ussdString"));

            return UssdRequest.builder()
                    .sessionId(sessionId)
                    .msisdn(msisdn)
                    .shortCode((String) body.get("serviceCode"))
                    .input(isNew ? "" : (String) body.getOrDefault("ussdString", ""))
                    .isNew(isNew)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse MTN Ghana request", e);
            throw new RuntimeException("Invalid MTN Ghana request", e);
        }
    }

    @Override
    public String formatResponse(UssdResponse response) {
        try {
            Map<String, Object> json = Map.of(
                    "responseString", response.getMessage(),
                    "action", response.isShouldContinue() ? "INPUT" : "BREAK"
            );
            return objectMapper.writeValueAsString(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to format MTN Ghana response", e);
        }
    }

    @Override
    public String getGatewayType() {
        return "MTN_GHANA";
    }
}
