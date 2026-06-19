package com.buddyai.agent.pipeline;

import com.buddyai.agent.entity.Slot;
import com.buddyai.agent.enums.StateSubType;
import com.buddyai.agent.enums.StateType;
import com.buddyai.agent.pipeline.dto.ConversationContext;
import com.buddyai.agent.pipeline.service.ApiExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentProcessor {

    private final ApiExecutionService apiExecutionService;
    private final ChatClient chatClient;

    public void process(ConversationContext ctx) {
        StateType state = ctx.getState();
        StateSubType subType = ctx.getStateSubType();

        if (state == StateType.EMPTY_STATE) {
            // Response already set (greeting or completion)
        } else if (state == StateType.INTENT_NOT_FOUND) {
            // responseText stays null — OrchestratorAgent handles via MCP tools
        } else if (state == StateType.SLOTS_INQUIRY && subType == StateSubType.IN_PROGRESS) {
            handleAskNextSlot(ctx);
        } else if (state == StateType.SLOTS_FOUND) {
            handleApiCall(ctx);
        }
    }

    private void handleAskNextSlot(ConversationContext ctx) {
        List<Slot> slots = ctx.getSlots();
        if (slots == null) {
            ctx.setResponseText("I need a bit more information. Could you please provide details?");
            return;
        }

        Optional<Slot> nextSlot = slots.stream()
                .filter(s -> Boolean.TRUE.equals(s.getRequired())
                        && !Boolean.TRUE.equals(s.getIsAuthParameter())
                        && (s.getSlotValue() == null || s.getSlotValue().isBlank()))
                .min(Comparator.comparingInt(s -> s.getSequence() != null ? s.getSequence() : 0));

        nextSlot.ifPresent(slot -> {
            // Mark current slot
            slots.forEach(s -> s.setIsCurrentSlot(false));
            slot.setIsCurrentSlot(true);

            // Build question text
            StringBuilder question = new StringBuilder();
            question.append(slot.getQuestion() != null
                    ? slot.getQuestion()
                    : "Please provide your " + slot.getName());

            if (slot.getAllowedValues() != null && !slot.getAllowedValues().isBlank()) {
                question.append(" (Options: ").append(slot.getAllowedValues()).append(")");
            }
            if (slot.getHint() != null && !slot.getHint().isBlank()) {
                question.append(" Hint: ").append(slot.getHint());
            }

            ctx.setResponseText(question.toString());
            log.debug("Asking for slot: {}", slot.getName());
        });

        if (nextSlot.isEmpty()) {
            ctx.setResponseText("Could you please provide more details?");
        }
    }

    private void handleApiCall(ConversationContext ctx) {
        Map<String, String> paramMap = new HashMap<>();
        if (ctx.getSlots() != null) {
            for (Slot slot : ctx.getSlots()) {
                paramMap.put(slot.getName(), slot.getSlotValue());
            }
        }

        String rawResponse;
        try {
            rawResponse = apiExecutionService.execute(ctx.getServiceId(), paramMap);
        } catch (Exception e) {
            log.error("API execution failed: {}", e.getMessage(), e);
            ctx.setResponseText("I'm sorry, I encountered an error processing your request. Please try again.");
            ctx.setState(StateType.EMPTY_STATE);
            return;
        }

        String finalResponse;

        if (ctx.getIntentResponse() != null && !ctx.getIntentResponse().isBlank()) {
            // Template substitution
            String template = ctx.getIntentResponse();
            for (Slot slot : ctx.getSlots()) {
                if (slot.getSlotValue() != null) {
                    template = template.replace("{" + slot.getName() + "}", slot.getSlotValue());
                }
            }
            finalResponse = template;
        } else if (ctx.getSummaryPrompt() != null && !ctx.getSummaryPrompt().isBlank()) {
            // Use custom summary prompt
            String summaryInput = ctx.getSummaryPrompt() + "\n\nAPI Response:\n" + rawResponse;
            finalResponse = chatClient.prompt()
                    .user(summaryInput)
                    .call()
                    .content();
        } else {
            // Default LLM summary
            String defaultPrompt = """
                Summarize this API response in a friendly, conversational way for the user.
                Original query: %s
                Response: %s""".formatted(ctx.getInput(), rawResponse);
            finalResponse = chatClient.prompt()
                    .user(defaultPrompt)
                    .call()
                    .content();
        }

        ctx.setResponseText(finalResponse);
        ctx.setState(StateType.EMPTY_STATE);
        log.info("API call completed for service {}, response summarized", ctx.getServiceId());
    }
}
