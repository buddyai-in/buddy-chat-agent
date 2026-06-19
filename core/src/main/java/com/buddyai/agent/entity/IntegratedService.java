package com.buddyai.agent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "integrated_service")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegratedService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "name")
    private String name;

    @Column(name = "endpoint")
    private String endpoint;

    @Column(name = "method")
    @Builder.Default
    private String method = "POST";

    @Lob
    @Column(name = "headers")
    private String headers;

    @Lob
    @Column(name = "system_prompt")
    private String systemPrompt;

    @Lob
    @Column(name = "slot_identify_prompt")
    private String slotIdentifyPrompt;

    @Lob
    @Column(name = "summary_prompt")
    private String summaryPrompt;

    @Lob
    @Column(name = "bot_response_template")
    private String botResponseTemplate;

    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;
}
