package com.buddyai.agent.pipeline;

import com.buddyai.agent.entity.ServiceParameter;
import com.buddyai.agent.entity.Slot;
import com.buddyai.agent.enums.StateSubType;
import com.buddyai.agent.enums.StateType;
import com.buddyai.agent.pipeline.dto.ConversationContext;
import com.buddyai.agent.repository.ServiceParameterRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SlotProcessor {

    private final ServiceParameterRepository serviceParameterRepository;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public void process(ConversationContext ctx) {
        if (ctx.getState() == StateType.INTENT_FOUND) {
            handleIntentFound(ctx);
        } else if (ctx.getState() == StateType.SLOTS_INQUIRY) {
            handleSlotsInquiry(ctx);
        }
    }

    private void handleIntentFound(ConversationContext ctx) {
        List<ServiceParameter> parameters = serviceParameterRepository
                .findByServiceIdOrderBySequenceAsc(ctx.getServiceId());

        List<Slot> slots = new ArrayList<>();
        for (ServiceParameter param : parameters) {
            Slot slot = Slot.builder()
                    .requestId(ctx.getRequestId())
                    .name(param.getName())
                    .paramType(param.getParamType())
                    .question(param.getQuestionToGetInput())
                    .required(param.getRequired())
                    .isAuthParameter(param.getIsAuthParameter())
                    .defaultValue(param.getDefaultValue())
                    .dateFormat(param.getDateFormat())
                    .sequence(param.getSequence())
                    .allowedValues(param.getAllowedValues())
                    .hint(param.getHint())
                    .build();

            // Resolve auth parameters from context metadata
            if (Boolean.TRUE.equals(param.getIsAuthParameter()) && ctx.getMetadata() != null) {
                Object val = ctx.getMetadata().get(param.getName());
                if (val == null) val = ctx.getMetadata().get(param.getName().toLowerCase());
                if (val == null && "userId".equalsIgnoreCase(param.getName())) val = ctx.getUserId();
                if (val == null && "clientId".equalsIgnoreCase(param.getName())) val = ctx.getClientId();
                if (val != null) slot.setSlotValue(val.toString());
            }

            // Apply default value if not yet set
            if (slot.getSlotValue() == null && param.getDefaultValue() != null) {
                slot.setSlotValue(param.getDefaultValue());
            }

            slots.add(slot);
        }

        // First-pass LLM extraction on current input
        extractSlotsWithLlm(ctx.getInput(), slots, parameters);

        ctx.setSlots(slots);
        transitionState(ctx);
    }

    private void handleSlotsInquiry(ConversationContext ctx) {
        List<Slot> slots = ctx.getSlots();
        if (slots == null) slots = new ArrayList<>();

        // Load parameters for LLM context (we only need names/types)
        List<ServiceParameter> parameters = serviceParameterRepository
                .findByServiceIdOrderBySequenceAsc(ctx.getServiceId());

        // LLM extraction on current user input
        extractSlotsWithLlm(ctx.getInput(), slots, parameters);

        ctx.setSlots(slots);
        transitionState(ctx);
    }

    private void transitionState(ConversationContext ctx) {
        List<Slot> slots = ctx.getSlots();
        boolean allFilled = slots.stream()
                .filter(s -> Boolean.TRUE.equals(s.getRequired()) && !Boolean.TRUE.equals(s.getIsAuthParameter()))
                .allMatch(s -> s.getSlotValue() != null && !s.getSlotValue().isBlank());

        if (allFilled) {
            ctx.setState(StateType.SLOTS_FOUND);
            ctx.setStateSubType(StateSubType.COMPLETED);
        } else {
            ctx.setState(StateType.SLOTS_INQUIRY);
            ctx.setStateSubType(StateSubType.IN_PROGRESS);
        }
    }

    private void extractSlotsWithLlm(String userInput, List<Slot> slots, List<ServiceParameter> parameters) {
        // Build slot description for non-auth, unfilled slots
        List<Slot> targetSlots = slots.stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsAuthParameter()) && s.getSlotValue() == null)
                .toList();

        if (targetSlots.isEmpty()) return;

        StringBuilder paramDesc = new StringBuilder();
        for (Slot slot : targetSlots) {
            paramDesc.append("- ").append(slot.getName())
                    .append(" (").append(slot.getParamType() != null ? slot.getParamType() : "string").append(")");
            if (slot.getAllowedValues() != null) {
                paramDesc.append(" [allowed: ").append(slot.getAllowedValues()).append("]");
            }
            if (slot.getHint() != null) {
                paramDesc.append(" — hint: ").append(slot.getHint());
            }
            paramDesc.append("\n");
        }

        String prompt = """
            Extract values for the following parameters from the user message.
            Return JSON only: {"param_name": "value", ...} — only include params where you found a value.
            If no value found for a param, omit it entirely.

            Parameters:
            %s

            User message: '%s'

            JSON:""".formatted(paramDesc.toString(), userInput);

        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .trim();

            // Extract JSON from response
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String json = response.substring(start, end + 1);
                Map<String, String> extracted = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});

                for (Slot slot : slots) {
                    if (extracted.containsKey(slot.getName())) {
                        String value = extracted.get(slot.getName());
                        if (value != null && !value.isBlank()) {
                            slot.setSlotValue(value);
                            log.debug("Slot extracted via LLM: {} = {}", slot.getName(), value);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("LLM slot extraction failed: {}", e.getMessage());
        }
    }
}
