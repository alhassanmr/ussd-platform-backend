package com.ussdplatform.controller;

import com.ussdplatform.dto.*;
import com.ussdplatform.model.*;
import com.ussdplatform.repository.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/apps")
@RequiredArgsConstructor
public class UssdAppController {

    private final UssdAppRepository appRepository;
    private final MenuRepository menuRepository;

    @GetMapping
    public List<AppDto> listApps(@AuthenticationPrincipal User user) {
        return appRepository.findByTenantId(user.getTenant().getId())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<AppDto> createApp(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateAppRequest request) {

        UssdApp app = UssdApp.builder()
                .tenant(user.getTenant())
                .name(request.getName())
                .description(request.getDescription())
                .shortCode(request.getShortCode())
                .gatewayType(UssdApp.GatewayType.valueOf(request.getGatewayType()))
                .gatewayConfig(request.getGatewayConfig())
                .status(UssdApp.AppStatus.DRAFT)
                .build();

        appRepository.save(app);

        // Auto-create a root menu
        Menu rootMenu = Menu.builder()
                .app(app)
                .name("Main Menu")
                .isRoot(true)
                .build();
        menuRepository.save(rootMenu);

        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(app));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppDto> getApp(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {

        return appRepository.findByIdAndTenantId(id, user.getTenant().getId())
                .map(app -> ResponseEntity.ok(toDto(app)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<AppDto> updateApp(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAppRequest request) {

        return appRepository.findByIdAndTenantId(id, user.getTenant().getId())
                .map(app -> {
                    if (request.getName() != null) app.setName(request.getName());
                    if (request.getDescription() != null) app.setDescription(request.getDescription());
                    if (request.getShortCode() != null) app.setShortCode(request.getShortCode());
                    if (request.getGatewayConfig() != null) app.setGatewayConfig(request.getGatewayConfig());
                    if (request.getStatus() != null) app.setStatus(UssdApp.AppStatus.valueOf(request.getStatus()));
                    return ResponseEntity.ok(toDto(appRepository.save(app)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteApp(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {

        return appRepository.findByIdAndTenantId(id, user.getTenant().getId())
                .map(app -> {
                    appRepository.delete(app);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/webhook-url")
    public ResponseEntity<java.util.Map<String, String>> getWebhookUrl(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @RequestHeader("X-Base-Url") String baseUrl) {

        return appRepository.findByIdAndTenantId(id, user.getTenant().getId())
                .map(app -> ResponseEntity.ok(java.util.Map.of(
                        "webhookUrl", baseUrl + "/ussd/webhook/" + app.getId(),
                        "appId", app.getId().toString()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    private AppDto toDto(UssdApp app) {
        return new AppDto(
                app.getId(),
                app.getName(),
                app.getDescription(),
                app.getShortCode(),
                app.getGatewayType().name(),
                app.getStatus().name(),
                app.getCreatedAt(),
                app.getUpdatedAt()
        );
    }
}
