package com.ussdplatform.dto;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.Map;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CreateAppRequest {
    @NotBlank public String name;
    public String description;
    public String shortCode;
    @NotBlank public String gatewayType;
    public Map<String, String> gatewayConfig;
}
