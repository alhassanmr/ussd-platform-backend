package com.ussdplatform.gateway;

import com.ussdplatform.model.UssdApp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the correct gateway adapter for a given app's gateway type.
 */
@Component
@RequiredArgsConstructor
public class GatewayFactory {

    private final List<UssdGateway> gateways;

    private Map<String, UssdGateway> gatewayMap;

    @jakarta.annotation.PostConstruct
    void init() {
        gatewayMap = gateways.stream()
                .collect(Collectors.toMap(
                        g -> g.getGatewayType().toUpperCase(),
                        Function.identity()
                ));
    }

    public UssdGateway getGateway(UssdApp.GatewayType type) {
        UssdGateway gateway = gatewayMap.get(type.name().toUpperCase());
        if (gateway == null) {
            throw new IllegalArgumentException("No gateway adapter for type: " + type);
        }
        return gateway;
    }
}
