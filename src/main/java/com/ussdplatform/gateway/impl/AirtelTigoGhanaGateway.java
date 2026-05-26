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
 * AirtelTigo Ghana Direct USSD Gateway Adapter
 *
 * AirtelTigo Ghana (merged Airtel + Tigo) direct connection
 * requires enterprise agreement with AirtelTigo Business.
 *
 * AirtelTigo sends JSON POST:
 * {
 *   "msisdn":       "233260000001",
 *   "sessionId":    "unique session ID",
 *   "ussdString":   "cumulative input (1*2*3 format)",
 *   "serviceCode":  "*714#",
 *   "newSession":   true | false,
 *   "network":      "AIRTELTIGO"
 * }
 *
 * AirtelTigo expects plain text response:
 *   "CON menu text"   — continue session
 *   "END end message" — end session
 *
 * Note: AirtelTigo uses same CON/END format as Africa's Talking.
 * Contact: business.airteltigo.com.gh
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AirtelTigoGhanaGateway implements UssdGateway {

    private final ObjectMapper objectMapper;

    @Override
    @SuppressWarnings("unchecked")
    public UssdRequest parseRequest(String rawBody) {
        try {
            Map<String, Object> body = objectMapper.readValue(rawBody, Map.class);

            boolean isNew  = Boolean.TRUE.equals(body.get("newSession"));
            String rawInput = (String) body.getOrDefault("ussdString", "");

            // AirtelTigo sends cumulative input separated by *
            String input = isNew ? "" : (rawInput.contains("*")
                    ? rawInput.substring(rawInput.lastIndexOf('*') + 1)
                    : rawInput);

            String msisdn = (String) body.getOrDefault("msisdn", "");
            if (msisdn != null && !msisdn.startsWith("+")) {
                msisdn = "+" + msisdn;
            }

            log.debug("AirtelTigo: session={} isNew={} input='{}'",
                    body.get("sessionId"), isNew, input);

            return UssdRequest.builder()
                    .sessionId((String) body.get("sessionId"))
                    .msisdn(msisdn)
                    .shortCode((String) body.get("serviceCode"))
                    .input(input)
                    .isNew(isNew)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse AirtelTigo Ghana request", e);
            throw new RuntimeException("Invalid AirtelTigo Ghana request", e);
        }
    }

    @Override
    public String formatResponse(UssdResponse response) {
        return (response.isShouldContinue() ? "CON " : "END ") + response.getMessage();
    }

    @Override
    public String getGatewayType() {
        return "AIRTELTIGO_GHANA";
    }
}
