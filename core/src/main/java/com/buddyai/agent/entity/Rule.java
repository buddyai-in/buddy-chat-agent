package com.buddyai.agent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "slot_rule")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_parameter_id")
    private Long serviceParameterId;

    @Lob
    @Column(name = "condition_expression")
    private String conditionExpression;

    @Lob
    @Column(name = "set_expression")
    private String setExpression;

    @Column(name = "target_field")
    private String targetField;

    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;
}
