package com.buddyai.agent.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Persistent record of a single agent session. One session maps to one
 * conversation thread across any number of {@link ConversationTurn} rows.
 */
@Entity
@Table(
        name = "agent_session",
        indexes = {
                @Index(name = "idx_agent_session_session_id", columnList = "session_id", unique = true),
                @Index(name = "idx_agent_session_client_user", columnList = "client_id, user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true, length = 128)
    private String sessionId;

    @Column(name = "client_id", nullable = false, length = 128)
    private String clientId;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    /**
     * Originating channel: REST, TELEGRAM, WHATSAPP, TEAMS, …
     */
    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    /**
     * Lifecycle state of the session.
     * Allowed values: {@code ACTIVE}, {@code COMPLETED}, {@code EXPIRED}.
     */
    @Column(name = "state", nullable = false, length = 16)
    @Builder.Default
    private String state = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Arbitrary JSON blob for channel-specific or client-specific context.
     * Stored as plain {@code text} so that no JSON column type is required across
     * all supported databases (PostgreSQL, H2 in tests, etc.).
     */
    @Lob
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "metadata", columnDefinition = "text")
    private String metadata;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
