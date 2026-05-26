package com.ussdplatform.dto;
import jakarta.validation.constraints.*;
import lombok.*;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RegisterRequest {
    @NotBlank @Email public String email;
    @NotBlank @Size(min = 6) public String password;
    @NotBlank public String fullName;
    @NotBlank public String companyName;

    @NotBlank(message = "Phone number is required")
    @Pattern(
        regexp = "^\\+[1-9]\\d{7,14}$",
        message = "Phone must be in international format e.g. +233244000001"
    )
    public String phone;
}
