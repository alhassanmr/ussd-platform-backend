package com.ussdplatform.dto;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class LoginRequest {
    @NotBlank(message = "Email or phone is required")
    public String email; // accepts email OR phone number in E.164 format

    @NotBlank(message = "Password is required")
    public String password;
}
