package com.buddyai.agent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(
    name = "service_intent",
    uniqueConstraints = @UniqueConstraint(columnNames = {"client_id", "intent_slug"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "intent_slug")
    private String intentSlug;

    @Column(name = "intent_type")
    private String intentType;

    @Lob
    @Column(name = "intent_response")
    private String intentResponse;

    @Lob
    @Column(name = "summary_prompt")
    private String summaryPrompt;

    @ElementCollection
    @CollectionTable(name = "service_intent_question", joinColumns = @JoinColumn(name = "service_intent_id"))
    @Column(name = "question")
    private List<String> questions;
}
