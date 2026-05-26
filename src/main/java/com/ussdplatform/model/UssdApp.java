package com.ussdplatform.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ussd_apps")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor(access = lombok.AccessLevel.PACKAGE) @Builder
public class UssdApp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "short_code")
    private String shortCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway_type", nullable = false)
    private GatewayType gatewayType;

    @Column(name = "gateway_config", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> gatewayConfig;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AppStatus status = AppStatus.DRAFT;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    public enum GatewayType { AFRICASTALKING, HUBTEL, CUSTOM }
    public enum AppStatus { DRAFT, ACTIVE, PAUSED }
}
