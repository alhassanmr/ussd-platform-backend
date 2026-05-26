package com.ussdplatform.dto;
import jakarta.validation.constraints.*;
import lombok.*;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RegisterRequest {
    @NotBlank @Email public String email;
    @NotBlank @Size(min = 6) public String password;
    @NotBlank public String fullName;
    @NotBlank public String companyName;
    public String phone;
}
