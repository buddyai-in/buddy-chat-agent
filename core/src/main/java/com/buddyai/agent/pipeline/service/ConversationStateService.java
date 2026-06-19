package com.buddyai.agent.pipeline.service;

import com.buddyai.agent.dto.AgentRequest;
import com.buddyai.agent.entity.ConversationState;
import com.buddyai.agent.entity.Slot;
import com.buddyai.agent.enums.StateSubType;
import com.buddyai.agent.enums.StateType;
import com.buddyai.agent.pipeline.dto.ConversationContext;
import com.buddyai.agent.repository.ConversationStateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ConversationStateService {

    private final ConversationStateRepository conversationStateRepository;
    private final ObjectMapper objectMapper;

    public Optional<ConversationContext> loadState(String sessionId, String clientId) {
        return conversationStateRepository.findBySessionIdAndCompleted(sessionId, false)
                .map(cs -> {
                    List<Slot> slots = deserializeSlots(cs.getSlotsJson());
                    return ConversationContext.builder()
                            .requestId(cs.getRequestId())
                            .sessionId(cs.getSessionId())
                            .clientId(cs.getClientId())
                            .state(parseStateType(cs.getState()))
                            .stateSubType(parseStateSubType(cs.getStateSubType()))
                            .intentSlug(cs.getIntentSlug())
                            .serviceId(cs.getServiceId())
                            .intentResponse(cs.getIntentResponse())
                            .summaryPrompt(cs.getSummaryPrompt())
                            .slots(slots)
                            .build();
                });
    }

    public void saveState(ConversationContext ctx) {
        String slotsJson = serializeSlots(ctx.getSlots());
        ConversationState cs = ConversationState.builder()
                .requestId(ctx.getRequestId())
                .sessionId(ctx.getSessionId())
                .clientId(ctx.getClientId())
                .state(ctx.getState() != null ? ctx.getState().name() : null)
                .stateSubType(ctx.getStateSubType() != null ? ctx.getStateSubType().name() : null)
                .inputText(ctx.getInput())
                .intentSlug(ctx.getIntentSlug())
                .serviceId(ctx.getServiceId())
                .intentResponse(ctx.getIntentResponse())
                .summaryPrompt(ctx.getSummaryPrompt())
                .slotsJson(slotsJson)
                .completed(false)
                .build();
        conversationStateRepository.save(cs);
    }

    public void completeState(String requestId) {
        conversationStateRepository.findById(requestId).ifPresent(cs -> {
            cs.setCompleted(true);
            conversationStateRepository.save(cs);
        });
    }

    public ConversationContext buildContext(AgentRequest request) {
        return ConversationContext.builder()
                .requestId(UUID.randomUUID().toString())
                .sessionId(request.sessionId())
                .clientId(request.clientId())
                .userId(request.userId())
                .input(request.message())
                .state(StateType.EMPTY_STATE)
                .stateSubType(StateSubType.EMPTY)
                .metadata(request.metadata() != null ? new java.util.HashMap<>(request.metadata()) : new java.util.HashMap<>())
                .build();
    }

    private String serializeSlots(List<Slot> slots) {
        if (slots == null) return "[]";
        try {
            return objectMapper.writeValueAsString(slots);
        } catch (Exception e) {
            log.error("Failed to serialize slots", e);
            return "[]";
        }
    }

    private List<Slot> deserializeSlots(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Slot>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize slots", e);
            return new ArrayList<>();
        }
    }

    private StateType parseStateType(String state) {
        if (state == null) return StateType.EMPTY_STATE;
        try {
            return StateType.valueOf(state);
        } catch (IllegalArgumentException e) {
            return StateType.EMPTY_STATE;
        }
    }

    private StateSubType parseStateSubType(String subType) {
        if (subType == null) return StateSubType.EMPTY;
        try {
            return StateSubType.valueOf(subType);
        } catch (IllegalArgumentException e) {
            return StateSubType.EMPTY;
        }
    }
}
