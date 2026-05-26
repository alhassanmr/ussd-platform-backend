package com.ussdplatform.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor(access = lombok.AccessLevel.PACKAGE) @Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.TRIAL;

    @Column(name = "paystack_customer_id")
    private String paystackCustomerId;

    @Column(name = "paystack_sub_code")
    private String paystackSubCode;

    @Column(name = "current_period_start")
    @Builder.Default
    private LocalDateTime currentPeriodStart = LocalDateTime.now();

    @Column(name = "current_period_end")
    @Builder.Default
    private LocalDateTime currentPeriodEnd = LocalDateTime.now().plusDays(30);

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    public enum SubscriptionStatus { ACTIVE, PAST_DUE, CANCELLED, TRIAL }
}
