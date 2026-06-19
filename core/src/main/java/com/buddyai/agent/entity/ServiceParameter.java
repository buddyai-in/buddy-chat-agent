package com.buddyai.agent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "service_parameter")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "name")
    private String name;

    @Column(name = "param_type")
    private String paramType;

    @Column(name = "required")
    @Builder.Default
    private Boolean required = true;

    @Lob
    @Column(name = "question_to_get_input")
    private String questionToGetInput;

    @Column(name = "default_value")
    private String defaultValue;

    @Column(name = "is_auth_parameter")
    @Builder.Default
    private Boolean isAuthParameter = false;

    @Column(name = "date_format")
    private String dateFormat;

    @Column(name = "sequence")
    @Builder.Default
    private Integer sequence = 0;

    @Lob
    @Column(name = "allowed_values")
    private String allowedValues;

    @Lob
    @Column(name = "hint")
    private String hint;

    @Column(name = "dependent_service_id")
    private Long dependentServiceId;

    @Lob
    @Column(name = "mapping_rules")
    private String mappingRules;

    @Column(name = "enable_llm_fallback")
    @Builder.Default
    private Boolean enableLlmFallback = false;
}
