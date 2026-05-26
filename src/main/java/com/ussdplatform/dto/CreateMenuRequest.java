package com.ussdplatform.dto;
import jakarta.validation.constraints.*;
import lombok.*;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CreateMenuRequest {
    @NotBlank public String name;
    public boolean root;
}
