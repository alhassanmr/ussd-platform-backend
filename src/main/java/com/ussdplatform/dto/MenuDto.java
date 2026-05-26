package com.ussdplatform.dto;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MenuDto {
    public UUID id;
    public String name;
    public boolean root;
    public List<MenuItemDto> items;
    public LocalDateTime createdAt;
}
