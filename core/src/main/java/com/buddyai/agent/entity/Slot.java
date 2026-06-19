package com.buddyai.agent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "slot_item")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "name")
    private String name;

    @Column(name = "param_type")
    private String paramType;

    @Lob
    @Column(name = "question")
    private String question;

    @Lob
    @Column(name = "slot_value")
    private String slotValue;

    @Column(name = "required")
    private Boolean required;

    @Column(name = "is_auth_parameter")
    @Builder.Default
    private Boolean isAuthParameter = false;

    @Column(name = "default_value")
    private String defaultValue;

    @Column(name = "date_format")
    private String dateFormat;

    @Column(name = "is_current_slot")
    @Builder.Default
    private Boolean isCurrentSlot = false;

    @Column(name = "sequence")
    @Builder.Default
    private Integer sequence = 0;

    @Lob
    @Column(name = "allowed_values")
    private String allowedValues;

    @Lob
    @Column(name = "hint")
    private String hint;
}
