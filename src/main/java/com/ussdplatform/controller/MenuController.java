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
@RequestMapping("/api/apps/{appId}/menus")
@RequiredArgsConstructor
public class MenuController {

    private final UssdAppRepository appRepository;
    private final MenuRepository menuRepository;
    private final MenuItemRepository menuItemRepository;

    @GetMapping
    public ResponseEntity<List<MenuDto>> listMenus(
            @AuthenticationPrincipal User user,
            @PathVariable UUID appId) {

        if (!appRepository.existsByIdAndTenantId(appId, user.getTenant().getId())) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(
                menuRepository.findByAppId(appId).stream()
                        .map(this::toDto)
                        .collect(Collectors.toList()));
    }

    @PostMapping
    public ResponseEntity<MenuDto> createMenu(
            @AuthenticationPrincipal User user,
            @PathVariable UUID appId,
            @Valid @RequestBody CreateMenuRequest request) {

        UssdApp app = appRepository.findByIdAndTenantId(appId, user.getTenant().getId())
                .orElse(null);
        if (app == null) return ResponseEntity.notFound().build();

        // If setting as root, unset any existing root
        if (request.isRoot()) {
            menuRepository.clearRootForApp(appId);
        }

        Menu menu = Menu.builder()
                .app(app)
                .name(request.getName())
                .isRoot(request.isRoot())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toDto(menuRepository.save(menu)));
    }

    @GetMapping("/{menuId}")
    public ResponseEntity<MenuDto> getMenu(
            @AuthenticationPrincipal User user,
            @PathVariable UUID appId,
            @PathVariable UUID menuId) {

        if (!appRepository.existsByIdAndTenantId(appId, user.getTenant().getId())) {
            return ResponseEntity.notFound().build();
        }
        return menuRepository.findById(menuId)
                .map(m -> ResponseEntity.ok(toDto(m)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{menuId}")
    public ResponseEntity<MenuDto> updateMenu(
            @AuthenticationPrincipal User user,
            @PathVariable UUID appId,
            @PathVariable UUID menuId,
            @Valid @RequestBody UpdateMenuRequest request) {

        if (!appRepository.existsByIdAndTenantId(appId, user.getTenant().getId())) {
            return ResponseEntity.notFound().build();
        }

        return menuRepository.findById(menuId)
                .map(menu -> {
                    if (request.getName() != null) menu.setName(request.getName());
                    if (request.isRoot()) {
                        menuRepository.clearRootForApp(appId);
                        menu.setRoot(true);
                    }
                    return ResponseEntity.ok(toDto(menuRepository.save(menu)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ---- Menu Items ----

    @PostMapping("/{menuId}/items")
    public ResponseEntity<MenuItemDto> addItem(
            @AuthenticationPrincipal User user,
            @PathVariable UUID appId,
            @PathVariable UUID menuId,
            @Valid @RequestBody CreateMenuItemRequest request) {

        if (!appRepository.existsByIdAndTenantId(appId, user.getTenant().getId())) {
            return ResponseEntity.notFound().build();
        }

        Menu menu = menuRepository.findById(menuId).orElse(null);
        if (menu == null) return ResponseEntity.notFound().build();

        MenuItem item = MenuItem.builder()
                .menu(menu)
                .itemType(MenuItem.ItemType.valueOf(request.getItemType()))
                .label(request.getLabel())
                .inputPrompt(request.getInputPrompt())
                .variableName(request.getVariableName())
                .webhookUrl(request.getWebhookUrl())
                .webhookMethod(request.getWebhookMethod())
                .endMessage(request.getEndMessage())
                .displayOrder(request.getDisplayOrder())
                .build();

        // Link to next menu if specified
        if (request.getNextMenuId() != null) {
            menuRepository.findById(request.getNextMenuId())
                    .ifPresent(item::setNextMenu);
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toItemDto(menuItemRepository.save(item)));
    }

    @PutMapping("/{menuId}/items/{itemId}")
    public ResponseEntity<MenuItemDto> updateItem(
            @AuthenticationPrincipal User user,
            @PathVariable UUID appId,
            @PathVariable UUID menuId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateMenuItemRequest request) {

        if (!appRepository.existsByIdAndTenantId(appId, user.getTenant().getId())) {
            return ResponseEntity.notFound().build();
        }

        return menuItemRepository.findById(itemId)
                .map(item -> {
                    if (request.getLabel() != null) item.setLabel(request.getLabel());
                    if (request.getInputPrompt() != null) item.setInputPrompt(request.getInputPrompt());
                    if (request.getVariableName() != null) item.setVariableName(request.getVariableName());
                    if (request.getWebhookUrl() != null) item.setWebhookUrl(request.getWebhookUrl());
                    if (request.getEndMessage() != null) item.setEndMessage(request.getEndMessage());
                    if (request.getDisplayOrder() != null) item.setDisplayOrder(request.getDisplayOrder());
                    if (request.getNextMenuId() != null) {
                        menuRepository.findById(request.getNextMenuId())
                                .ifPresent(item::setNextMenu);
                    }
                    return ResponseEntity.ok(toItemDto(menuItemRepository.save(item)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{menuId}/items/{itemId}")
    public ResponseEntity<Void> deleteItem(
            @AuthenticationPrincipal User user,
            @PathVariable UUID appId,
            @PathVariable UUID menuId,
            @PathVariable UUID itemId) {

        if (!appRepository.existsByIdAndTenantId(appId, user.getTenant().getId())) {
            return ResponseEntity.notFound().build();
        }

        menuItemRepository.findById(itemId).ifPresent(menuItemRepository::delete);
        return ResponseEntity.noContent().build();
    }

    private MenuDto toDto(Menu menu) {
        return new MenuDto(
                menu.getId(),
                menu.getName(),
                menu.isRoot(),
                menu.getItems().stream().map(this::toItemDto).collect(Collectors.toList()),
                menu.getCreatedAt()
        );
    }

    private MenuItemDto toItemDto(MenuItem item) {
        return new MenuItemDto(
                item.getId(),
                item.getItemType().name(),
                item.getLabel(),
                item.getInputPrompt(),
                item.getVariableName(),
                item.getNextMenu() != null ? item.getNextMenu().getId() : null,
                item.getWebhookUrl(),
                item.getEndMessage(),
                item.getDisplayOrder()
        );
    }
}
