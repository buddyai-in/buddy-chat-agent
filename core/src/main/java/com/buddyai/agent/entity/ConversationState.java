package com.buddyai.agent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationState {

    @Id
    @Column(name = "request_id")
    private String requestId;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "state")
    private String state;

    @Column(name = "state_sub_type")
    private String stateSubType;

    @Lob
    @Column(name = "input_text")
    private String inputText;

    @Column(name = "intent_slug")
    private String intentSlug;

    @Column(name = "service_id")
    private Long serviceId;

    @Lob
    @Column(name = "intent_response")
    private String intentResponse;

    @Lob
    @Column(name = "summary_prompt")
    private String summaryPrompt;

    @Lob
    @Column(name = "slots_json")
    private String slotsJson;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed")
    @Builder.Default
    private Boolean completed = false;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
