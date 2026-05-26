package com.ussdplatform.dto;
import lombok.*;
import java.util.UUID;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class UserDto {
    public UUID id;
    public String email;
    public String fullName;
    public String role;
    public UUID tenantId;
    public String tenantName;
    public String tenantSlug;
    public String plan;
}
