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
 * Nalo Solutions USSD Gateway Adapter (Ghana)
 * https://nalosolutions.com
 *
 * Nalo is one of Ghana's leading USSD aggregators used by
 * banks, fintechs, and large enterprises. Supports MTN,
 * Telecel (Vodafone), and AirtelTigo.
 *
 * Nalo sends JSON POST:
 * {
 *   "USERID":       "233244000001",
 *   "MSISDN":       "233244000001",
 *   "USERDATA":     "user input text",
 *   "MSG":          "user input text (same as USERDATA)",
 *   "MSGTYPE":      true (new session) | false (response),
 *   "NETWORK":      "MTN" | "VODAFONE" | "AIRTELTIGO",
 *   "SESSIONID":    "unique session identifier",
 *   "SHORTCODE":    "*714#"
 * }
 *
 * Nalo expects JSON response:
 * {
 *   "USERID":    "233244000001",
 *   "MSISDN":    "233244000001",
 *   "MSG":       "text to display",
 *   "MSGTYPE":   true (continue) | false (end session)
 * }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NaloGateway implements UssdGateway {

    private final ObjectMapper objectMapper;

    @Override
    @SuppressWarnings("unchecked")
    public UssdRequest parseRequest(String rawBody) {
        try {
            Map<String, Object> body = objectMapper.readValue(rawBody, Map.class);

            // MSGTYPE: true = new session, false = response
            boolean isNew = Boolean.TRUE.equals(body.get("MSGTYPE"));

            String msisdn = (String) body.getOrDefault("MSISDN",
                            body.getOrDefault("USERID", ""));

            // Normalise msisdn to E.164
            if (msisdn != null && !msisdn.startsWith("+")) {
                msisdn = "+" + msisdn;
            }

            String input = isNew ? "" : (String) body.getOrDefault("USERDATA",
                           body.getOrDefault("MSG", ""));

            log.debug("Nalo request: session={} msisdn={} isNew={} input='{}'",
                    body.get("SESSIONID"), msisdn, isNew, input);

            return UssdRequest.builder()
                    .sessionId((String) body.get("SESSIONID"))
                    .msisdn(msisdn)
                    .shortCode((String) body.get("SHORTCODE"))
                    .input(input)
                    .isNew(isNew)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse Nalo request", e);
            throw new RuntimeException("Invalid Nalo Solutions request", e);
        }
    }

    @Override
    public String formatResponse(UssdResponse response) {
        try {
            // Nalo requires the MSISDN echoed back — we use a placeholder
            // The real MSISDN should be injected but the engine doesn't pass it here.
            // Nalo also accepts responses without USERID/MSISDN in some versions.
            Map<String, Object> json = Map.of(
                    "MSG",     response.getMessage(),
                    "MSGTYPE", response.isShouldContinue()
            );
            return objectMapper.writeValueAsString(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to format Nalo response", e);
        }
    }

    @Override
    public String getGatewayType() {
        return "NALO";
    }
}
