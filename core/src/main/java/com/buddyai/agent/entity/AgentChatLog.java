package com.buddyai.agent.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * Persistent audit log entry for every chat interaction handled by the agent.
 * One row is written per request/response cycle, regardless of success or failure.
 */
@Entity
@Table(
        name = "agent_chat_log",
        indexes = {
                @Index(name = "idx_chat_log_client_id", columnList = "client_id"),
                @Index(name = "idx_chat_log_session_id", columnList = "session_id"),
                @Index(name = "idx_chat_log_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class AgentChatLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "client_id", nullable = false, length = 128)
    private String clientId;

    @Column(name = "user_id", length = 128)
    private String userId;

    /** Originating channel: REST, TELEGRAM, WHATSAPP, TEAMS, … */
    @Column(name = "channel", length = 32)
    private String channel;

    /** Full text of the user's inbound message. */
    @Lob
    @Column(name = "user_message", columnDefinition = "text")
    private String userMessage;

    /** Full text of the agent's outbound response. */
    @Lob
    @Column(name = "agent_response", columnDefinition = "text")
    private String agentResponse;

    /** Name of the agent that handled the request (e.g. OrchestratorAgent). */
    @Column(name = "agent_used", length = 128)
    private String agentUsed;

    /** Comma-separated list of MCP tool names that were called during this turn. */
    @Column(name = "tools_called", length = 1024)
    private String toolsCalled;

    /** Wall-clock time in milliseconds from request receipt to response dispatch. */
    @Column(name = "duration_ms")
    private Long durationMs;

    /**
     * Final outcome of the interaction.
     * Allowed values: {@code SUCCESS}, {@code ERROR}.
     */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** Human-readable error description populated only when {@code status} is {@code ERROR}. */
    @Column(name = "error_message", length = 2048)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
