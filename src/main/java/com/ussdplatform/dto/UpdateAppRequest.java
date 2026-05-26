package com.ussdplatform.dto;
import lombok.*;
import java.util.Map;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class UpdateAppRequest {
    public String name;
    public String description;
    public String shortCode;
    public String status;
    public Map<String, String> gatewayConfig;
}
