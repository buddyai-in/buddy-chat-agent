package com.buddyai.agent.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stores API credentials for each client tenant.
 * The {@link #secretKey} is matched against the Bearer token supplied in the
 * {@code Authorization} header for every inbound request.
 */
@Entity
@Table(
        name = "client_auth",
        indexes = {
                @Index(name = "idx_client_auth_client_id", columnList = "client_id", unique = true)
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, unique = true, length = 128)
    private String clientId;

    /** Hashed or plain secret that must match the Bearer token in the request. */
    @Column(name = "secret_key", nullable = false, length = 512)
    private String secretKey;

    /** Human-readable display name for the client / tenant. */
    @Column(name = "client_name", length = 256)
    private String clientName;

    /**
     * When {@code false} the client is suspended; the interceptor will reject
     * all requests even if the secret matches.
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
