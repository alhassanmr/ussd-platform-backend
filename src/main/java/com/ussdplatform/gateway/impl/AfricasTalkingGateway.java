package com.ussdplatform.gateway.impl;

import com.ussdplatform.gateway.UssdGateway;
import com.ussdplatform.gateway.UssdRequest;
import com.ussdplatform.gateway.UssdResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Africa's Talking USSD Gateway Adapter.
 *
 * AT sends a form-encoded POST with fields:
 *   sessionId, phoneNumber, networkCode, serviceCode, text
 *
 * AT expects a plain-text response starting with "CON " or "END ".
 */
@Component
public class AfricasTalkingGateway implements UssdGateway {

    @Override
    public UssdRequest parseRequest(String rawBody) {
        Map<String, String> params = parseFormEncoded(rawBody);

        String text = params.getOrDefault("text", "");
        boolean isNew = text.isEmpty();

        // AT sends cumulative input separated by '*'
        // e.g. first input: "1", second: "1*2", third: "1*2*hello"
        // We only want the last segment as the current input
        String currentInput = isNew ? "" : text.contains("*")
                ? text.substring(text.lastIndexOf('*') + 1)
                : text;

        return UssdRequest.builder()
                .sessionId(params.get("sessionId"))
                .msisdn(params.get("phoneNumber"))
                .shortCode(params.get("serviceCode"))
                .input(currentInput)
                .isNew(isNew)
                .build();
    }

    @Override
    public String formatResponse(UssdResponse response) {
        String prefix = response.isShouldContinue() ? "CON " : "END ";
        return prefix + response.getMessage();
    }

    @Override
    public String getGatewayType() {
        return "AFRICASTALKING";
    }

    private Map<String, String> parseFormEncoded(String body) {
        return Arrays.stream(body.split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(
                        parts -> UriUtils.decode(parts[0], StandardCharsets.UTF_8),
                        parts -> UriUtils.decode(parts[1], StandardCharsets.UTF_8)
                ));
    }
}
