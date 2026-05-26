package com.ussdplatform.dto;
import lombok.*;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AuthResponse {
    public String token;
    public UserDto user;
    public String error;
}
