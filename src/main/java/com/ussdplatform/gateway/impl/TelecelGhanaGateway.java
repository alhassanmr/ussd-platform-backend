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
 * Telecel Ghana Direct USSD Gateway Adapter
 * (formerly Vodafone Ghana)
 *
 * Telecel Ghana direct connection requires enterprise agreement.
 * Contact: Telecel Ghana Business or developer portal.
 *
 * Telecel Ghana sends JSON POST:
 * {
 *   "msisdn":        "233200000001",
 *   "sessionId":     "unique session ID",
 *   "input":         "user input",
 *   "shortCode":     "*714#",
 *   "type":          "1" (new) | "2" (response) | "3" (end/timeout),
 *   "operator":      "TELECEL"
 * }
 *
 * Telecel expects JSON response:
 * {
 *   "message":       "text to display",
 *   "continueSession": true | false
 * }
 *
 * Note: Telecel recently rebranded from Vodafone Ghana (2023).
 * Contact: business.telecel.com.gh
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TelecelGhanaGateway implements UssdGateway {

    private final ObjectMapper objectMapper;

    @Override
    @SuppressWarnings("unchecked")
    public UssdRequest parseRequest(String rawBody) {
        try {
            Map<String, Object> body = objectMapper.readValue(rawBody, Map.class);

            String type    = String.valueOf(body.getOrDefault("type", "2"));
            boolean isNew  = "1".equals(type);
            boolean isEnd  = "3".equals(type);

            String msisdn  = (String) body.getOrDefault("msisdn", "");
            if (msisdn != null && !msisdn.startsWith("+")) {
                msisdn = "+" + msisdn;
            }

            log.debug("Telecel Ghana: session={} type={} input='{}'",
                    body.get("sessionId"), type, body.get("input"));

            return UssdRequest.builder()
                    .sessionId((String) body.get("sessionId"))
                    .msisdn(msisdn)
                    .shortCode((String) body.get("shortCode"))
                    .input(isNew ? "" : (String) body.getOrDefault("input", ""))
                    .isNew(isNew)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse Telecel Ghana request", e);
            throw new RuntimeException("Invalid Telecel Ghana request", e);
        }
    }

    @Override
    public String formatResponse(UssdResponse response) {
        try {
            Map<String, Object> json = Map.of(
                    "message",         response.getMessage(),
                    "continueSession", response.isShouldContinue()
            );
            return objectMapper.writeValueAsString(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to format Telecel Ghana response", e);
        }
    }

    @Override
    public String getGatewayType() {
        return "TELECEL_GHANA";
    }
}
