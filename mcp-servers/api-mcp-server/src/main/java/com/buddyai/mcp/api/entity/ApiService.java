package com.buddyai.mcp.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "service")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String endpoint;

    @Column(nullable = false)
    private String method;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Lob
    @Column(name = "request_schema", columnDefinition = "TEXT")
    private String requestSchema;

    @Lob
    @Column(name = "response_schema", columnDefinition = "TEXT")
    private String responseSchema;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "auth_type")
    private String authType;

    @Column(name = "api_key")
    private String apiKey;

    @Column(nullable = false)
    private boolean enabled;
}
