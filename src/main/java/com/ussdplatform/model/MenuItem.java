package com.ussdplatform.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "menu_items")
@Getter
@Setter
@NoArgsConstructor
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private MenuItem parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    @OrderBy("displayOrder ASC")
    private List<MenuItem> children = new ArrayList<>();

    @Column(name = "display_order")
    private int displayOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private ItemType itemType;

    @Column(nullable = false)
    private String label;

    @Column(name = "input_prompt")
    private String inputPrompt;

    @Column(name = "variable_name")
    private String variableName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "next_menu_id")
    private Menu nextMenu;

    @Column(name = "webhook_url")
    private String webhookUrl;

    @Column(name = "webhook_method")
    private String webhookMethod = "POST";

    @Column(name = "end_message")
    private String endMessage;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    // Manual builder to avoid @Builder + @Builder.Default conflicts with @AllArgsConstructor
    public static MenuItemBuilder builder() { return new MenuItemBuilder(); }

    public static class MenuItemBuilder {
        private UUID id;
        private Menu menu;
        private MenuItem parent;
        private List<MenuItem> children = new ArrayList<>();
        private int displayOrder = 0;
        private ItemType itemType;
        private String label;
        private String inputPrompt;
        private String variableName;
        private Menu nextMenu;
        private String webhookUrl;
        private String webhookMethod = "POST";
        private String endMessage;
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime updatedAt = LocalDateTime.now();

        public MenuItemBuilder id(UUID v) { this.id = v; return this; }
        public MenuItemBuilder menu(Menu v) { this.menu = v; return this; }
        public MenuItemBuilder parent(MenuItem v) { this.parent = v; return this; }
        public MenuItemBuilder children(List<MenuItem> v) { this.children = v; return this; }
        public MenuItemBuilder displayOrder(int v) { this.displayOrder = v; return this; }
        public MenuItemBuilder itemType(ItemType v) { this.itemType = v; return this; }
        public MenuItemBuilder label(String v) { this.label = v; return this; }
        public MenuItemBuilder inputPrompt(String v) { this.inputPrompt = v; return this; }
        public MenuItemBuilder variableName(String v) { this.variableName = v; return this; }
        public MenuItemBuilder nextMenu(Menu v) { this.nextMenu = v; return this; }
        public MenuItemBuilder webhookUrl(String v) { this.webhookUrl = v; return this; }
        public MenuItemBuilder webhookMethod(String v) { this.webhookMethod = v; return this; }
        public MenuItemBuilder endMessage(String v) { this.endMessage = v; return this; }

        public MenuItem build() {
            MenuItem m = new MenuItem();
            m.id = id; m.menu = menu; m.parent = parent;
            m.children = children; m.displayOrder = displayOrder;
            m.itemType = itemType; m.label = label;
            m.inputPrompt = inputPrompt; m.variableName = variableName;
            m.nextMenu = nextMenu; m.webhookUrl = webhookUrl;
            m.webhookMethod = webhookMethod; m.endMessage = endMessage;
            m.createdAt = createdAt; m.updatedAt = updatedAt;
            return m;
        }
    }

    public enum ItemType { DISPLAY, INPUT, ROUTER, WEBHOOK, END }
}
