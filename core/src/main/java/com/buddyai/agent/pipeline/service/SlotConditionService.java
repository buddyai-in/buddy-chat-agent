package com.buddyai.agent.pipeline.service;

import com.buddyai.agent.entity.Rule;
import com.buddyai.agent.entity.ServiceParameter;
import com.buddyai.agent.entity.Slot;
import com.buddyai.agent.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlotConditionService {

    private final RuleRepository ruleRepository;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    public void applyRules(List<Slot> slots, List<ServiceParameter> parameters) {
        // Build a map of current slot values for SpEL evaluation
        Map<String, String> slotValues = new HashMap<>();
        for (Slot slot : slots) {
            if (slot.getSlotValue() != null) {
                slotValues.put(slot.getName(), slot.getSlotValue());
            }
        }

        // Build slot lookup map for quick updates
        Map<String, Slot> slotMap = new HashMap<>();
        for (Slot slot : slots) {
            slotMap.put(slot.getName(), slot);
        }

        for (ServiceParameter param : parameters) {
            List<Rule> rules = ruleRepository.findByServiceParameterIdOrderByPriorityAsc(param.getId());
            for (Rule rule : rules) {
                if (!Boolean.TRUE.equals(rule.getActive())) continue;
                try {
                    EvaluationContext ctx = new StandardEvaluationContext(slotValues);
                    Boolean conditionResult = expressionParser
                            .parseExpression(rule.getConditionExpression())
                            .getValue(ctx, Boolean.class);

                    if (Boolean.TRUE.equals(conditionResult)) {
                        String newValue = expressionParser
                                .parseExpression(rule.getSetExpression())
                                .getValue(ctx, String.class);

                        String targetField = rule.getTargetField();
                        if (targetField != null && slotMap.containsKey(targetField)) {
                            slotMap.get(targetField).setSlotValue(newValue);
                            slotValues.put(targetField, newValue);
                            log.debug("Rule applied: {} -> {} = {}", rule.getConditionExpression(), targetField, newValue);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to evaluate rule {}: {}", rule.getId(), e.getMessage());
                }
            }
        }
    }
}
