package com.ussdplatform.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor(access = lombok.AccessLevel.PACKAGE) @Builder
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(nullable = false)
    private String type;

    private String subject;

    @Column(nullable = false)
    @Builder.Default
    private String status = "SENT";

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "sent_at", updatable = false)
    @Builder.Default
    private LocalDateTime sentAt = LocalDateTime.now();
}
