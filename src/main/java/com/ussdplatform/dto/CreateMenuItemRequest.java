package com.ussdplatform.dto;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.UUID;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CreateMenuItemRequest {
    @NotBlank public String itemType;
    @NotBlank public String label;
    public String inputPrompt;
    public String variableName;
    public UUID nextMenuId;
    public String webhookUrl;
    public String webhookMethod;
    public String endMessage;
    public int displayOrder;
}
