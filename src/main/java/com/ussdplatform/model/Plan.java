package com.ussdplatform.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "plans")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor(access = lombok.AccessLevel.PACKAGE) @Builder
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "price_ghs", nullable = false)
    @Builder.Default
    private BigDecimal priceGhs = BigDecimal.ZERO;

    @Column(name = "max_apps", nullable = false)
    @Builder.Default
    private int maxApps = 1;

    @Column(name = "max_sessions", nullable = false)
    @Builder.Default
    private int maxSessions = 500;

    @Column(name = "extra_session_fee")
    @Builder.Default
    private BigDecimal extraSessionFee = BigDecimal.ZERO;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
