package com.ussdplatform.dto;
import lombok.*;
import java.util.UUID;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class UpdateMenuItemRequest {
    public String label;
    public String inputPrompt;
    public String variableName;
    public UUID nextMenuId;
    public String webhookUrl;
    public String endMessage;
    public Integer displayOrder;
}
