package com.ussdplatform.dto;
import lombok.*;
import java.util.UUID;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MenuItemDto {
    public UUID id;
    public String itemType;
    public String label;
    public String inputPrompt;
    public String variableName;
    public UUID nextMenuId;
    public String webhookUrl;
    public String endMessage;
    public int displayOrder;
}
