package com.trustvault.backend.audit;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(length = 500)
    private String details;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    protected AuditLog() {
    }

    public AuditLog(
            String username,
            String action,
            String details,
            LocalDateTime occurredAt) {
        this.username = username;
        this.action = action;
        this.details = details;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}