package com.buddyai.agent.pipeline;

import com.buddyai.agent.enums.StateSubType;
import com.buddyai.agent.enums.StateType;
import com.buddyai.agent.pipeline.dto.ConversationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionProcessor {

    private final IntentProcessor intentProcessor;
    private final SlotProcessor slotProcessor;
    private final ContentProcessor contentProcessor;

    public void process(ConversationContext ctx) {
        StateType state = ctx.getState();
        StateSubType subType = ctx.getStateSubType();

        log.debug("DecisionProcessor — state={} subType={}", state, subType);

        if (state == null || state == StateType.EMPTY_STATE) {
            // Identify intent first
            intentProcessor.process(ctx);

            // After intent processing, if intent found, move to slot filling
            if (ctx.getState() == StateType.INTENT_FOUND) {
                slotProcessor.process(ctx);
                contentProcessor.process(ctx);
            } else {
                // EMPTY_STATE (greeting) or INTENT_NOT_FOUND
                contentProcessor.process(ctx);
            }

        } else if (state == StateType.INTENT_FOUND) {
            slotProcessor.process(ctx);
            contentProcessor.process(ctx);

        } else if (state == StateType.SLOTS_INQUIRY) {
            // User is providing slot values
            slotProcessor.process(ctx);
            contentProcessor.process(ctx);

        } else if (state == StateType.INTENT_NOT_FOUND) {
            contentProcessor.process(ctx);

        } else if (state == StateType.SLOTS_FOUND) {
            contentProcessor.process(ctx);
        }
    }
}
