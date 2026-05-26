package com.ussdplatform.dto;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AppDto {
    public UUID id;
    public String name;
    public String description;
    public String shortCode;
    public String gatewayType;
    public String status;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
