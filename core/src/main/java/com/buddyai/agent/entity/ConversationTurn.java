package com.buddyai.agent.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A single turn (message) in a conversation, used as episodic memory.
 * Turns are ordered within a session by {@link #sequenceNum}.
 */
@Entity
@Table(
        name = "conversation_turn",
        indexes = {
                @Index(name = "idx_conv_turn_session_id", columnList = "session_id"),
                @Index(name = "idx_conv_turn_session_seq", columnList = "session_id, sequence_num")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Foreign key reference to the owning session (denormalised for fast lookup). */
    @Column(name = "session_id", nullable = false, length = 128)
    private String sessionId;

    /**
     * Message role.
     * Allowed values: {@code USER}, {@code ASSISTANT}, {@code TOOL}.
     */
    @Column(name = "role", nullable = false, length = 16)
    private String role;

    /** Full text content of the message. Stored as {@code text} / CLOB. */
    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    /**
     * Name of the tool that produced this turn.
     * Only populated when {@code role} is {@code TOOL}.
     */
    @Column(name = "tool_name", length = 128)
    private String toolName;

    /**
     * 1-based monotonically increasing index within the session.
     * Used for deterministic ordering without relying on {@code id} gaps.
     */
    @Column(name = "sequence_num", nullable = false)
    private Integer sequenceNum;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
