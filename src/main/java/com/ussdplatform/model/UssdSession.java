package com.ussdplatform.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ussd_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor(access = lombok.AccessLevel.PACKAGE) @Builder
public class UssdSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id", nullable = false)
    private UssdApp app;

    @Column(nullable = false)
    private String msisdn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_menu_id")
    private Menu currentMenu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_item_id")
    private MenuItem currentItem;

    @Column(name = "session_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    private Map<String, String> sessionData = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(name = "started_at", updatable = false)
    @Builder.Default
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "last_activity")
    @Builder.Default
    private LocalDateTime lastActivity = LocalDateTime.now();

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    public enum SessionStatus { ACTIVE, COMPLETED, TIMEOUT }
}
