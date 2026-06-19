package com.buddyai.agent.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * A single semantic-memory fact about a user.
 *
 * <p>The {@link #embedding} column uses PgVector's {@code vector(1536)} type
 * (compatible with OpenAI and Anthropic 1536-dimensional embeddings).
 * For databases other than PostgreSQL (e.g. H2 in tests) the column falls back
 * to a float array stored by Hibernate; adjust the dialect accordingly.</p>
 */
@Entity
@Table(
        name = "user_memory",
        indexes = {
                @Index(name = "idx_user_memory_user_id", columnList = "user_id"),
                @Index(name = "idx_user_memory_user_client", columnList = "user_id, client_id"),
                @Index(name = "idx_user_memory_category", columnList = "user_id, category")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "client_id", nullable = false, length = 128)
    private String clientId;

    /** The human-readable fact extracted from the conversation. */
    @Lob
    @Column(name = "fact", nullable = false, columnDefinition = "text")
    private String fact;

    /**
     * Semantic category for this memory entry.
     * Allowed values: {@code PREFERENCE}, {@code HISTORY}, {@code PROFILE}, {@code CONTEXT}.
     */
    @Column(name = "category", nullable = false, length = 32)
    private String category;

    /**
     * Vector embedding of {@link #fact}.
     * Mapped to PgVector's {@code vector(1536)} column type in PostgreSQL.
     */
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_accessed_at", nullable = false)
    private LocalDateTime lastAccessedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.lastAccessedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastAccessedAt = LocalDateTime.now();
    }
}
