package com.ussdplatform.gateway;

/**
 * Pluggable gateway adapter interface.
 * Each telecom provider (Africa's Talking, Hubtel, etc.) implements this.
 */
public interface UssdGateway {

    /**
     * Parse the raw HTTP request body from the gateway into a normalized UssdRequest.
     */
    UssdRequest parseRequest(String rawBody);

    /**
     * Serialize a UssdResponse into the format expected by this gateway.
     */
    String formatResponse(UssdResponse response);

    /**
     * Return the gateway type identifier.
     */
    String getGatewayType();
}
