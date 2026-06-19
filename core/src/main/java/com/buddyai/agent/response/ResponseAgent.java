package com.buddyai.agent.response;

import com.buddyai.agent.entity.Slot;
import com.buddyai.agent.enums.ResponseOutcome;
import com.buddyai.agent.enums.StateType;
import com.buddyai.agent.pipeline.dto.ApiResult;
import com.buddyai.agent.pipeline.dto.ConversationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Agentic response handler — the replacement for the old two-axis
 * {@code ResponseHandler}/{@code MessageHandler} registry.
 *
 * <p>Rather than dispatching to a hand-written handler per service type and
 * status code, it hands the LLM:</p>
 * <ul>
 *   <li>the deterministic {@link ResponseOutcome} (so it never guesses success)</li>
 *   <li>the original user query and the filled slots</li>
 *   <li>the raw API response body</li>
 * </ul>
 * <p>…plus a small toolset ({@link ResponseTools}) for the cases that need
 * determinism: CSV export, file links, and steering the state machine back to
 * slot correction on a 4xx.</p>
 *
 * <p>Two deterministic short-circuits run <em>before</em> the model, for cost
 * and exact-wording control:</p>
 * <ol>
 *   <li>a fixed {@code botResponseTemplate} on the service → rendered directly;</li>
 *   <li>{@code UNREACHABLE} → a fixed apology (no point asking the LLM).</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResponseAgent {

    private final ChatClient chatClient;
    private final DownloadService downloadService;
    private final TemplateEngine templateEngine;
    private final ObjectMapper objectMapper;

    /**
     * Produce the user-facing reply for a completed service call and write it
     * into {@code ctx} (responseText / responseType / downloadLink). May also
     * mutate {@code ctx.state} back to SLOTS_INQUIRY via the correction tool.
     */
    public void generate(ConversationContext ctx, ApiResult result, ResponseOutcome outcome) {
        // 1. Deterministic template override — business wants exact wording.
        String template = ctx.getService() != null ? ctx.getService().getBotResponseTemplate() : null;
        if (outcome == ResponseOutcome.OK && template != null && !template.isBlank()) {
            String rendered = templateEngine.render(template, result.body());
            ctx.setResponseText(rendered);
            ctx.setResponseType("TEXT");
            ctx.setState(StateType.EMPTY_STATE);
            log.info("ResponseAgent: rendered fixed template for service {}", ctx.getServiceId());
            return;
        }

        // 2. Transport failure — no value in invoking the LLM.
        if (outcome == ResponseOutcome.UNREACHABLE) {
            ctx.setResponseText("I couldn't reach the service to complete your request right now. "
                    + "Please try again in a moment.");
            ctx.setResponseType("TEXT");
            ctx.setState(StateType.EMPTY_STATE);
            return;
        }

        // 3. Agentic path — let the model decide presentation, with tools.
        ResponseTools tools = new ResponseTools(ctx, downloadService, objectMapper);
        String prompt = buildPrompt(ctx, result, outcome);

        String reply;
        try {
            reply = chatClient.prompt()
                    .system("""
                        You are the response-presentation step of an AI assistant. An action/API has just run.
                        Decide how to present the OUTCOME to the user — you do NOT decide whether it succeeded;
                        that is given to you.

                        Guidelines:
                        - OK: answer the user's original request from the response data in a clear, friendly,
                          conversational way. If the data is a large/tabular list better delivered as a file,
                          call formatAsCsv and share the link. If it is a file/media payload (base64), call
                          generateDownloadLink. Otherwise just summarize.
                        - CLIENT_ERROR: the input was rejected. Identify the specific field at fault and call
                          requestSlotCorrection(slotName, reason), then write a short message telling the user
                          what to fix. Use the exact slot name from the provided slot list.
                        - SERVER_ERROR: apologize briefly, say it's a temporary problem on the service side, and
                          suggest trying again. Do not expose stack traces.
                        Keep replies concise. Never invent data that isn't in the response.
                        """)
                    .user(prompt)
                    .tools(tools)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("ResponseAgent LLM call failed: {}", e.getMessage(), e);
            ctx.setResponseText("Sorry, I ran into a problem preparing the response. Please try again.");
            ctx.setResponseType("TEXT");
            ctx.setState(StateType.EMPTY_STATE);
            return;
        }

        ctx.setResponseText(reply);
        if (ctx.getResponseType() == null) {
            ctx.setResponseType("TEXT");
        }
        // If a tool moved us back to SLOTS_INQUIRY, leave that state intact;
        // otherwise the turn is complete.
        if (ctx.getState() == StateType.SLOTS_FOUND) {
            ctx.setState(StateType.EMPTY_STATE);
        }
    }

    private String buildPrompt(ConversationContext ctx, ApiResult result, ResponseOutcome outcome) {
        String slotSummary = (ctx.getSlots() == null || ctx.getSlots().isEmpty())
                ? "(none)"
                : ctx.getSlots().stream()
                    .map(Slot::getName)
                    .collect(Collectors.joining(", "));

        String slotValues = (ctx.getSlots() == null) ? "(none)"
                : ctx.getSlots().stream()
                    .filter(s -> s.getSlotValue() != null)
                    .map(s -> s.getName() + "=" + s.getSlotValue())
                    .collect(Collectors.joining(", "));

        return """
            Outcome: %s
            HTTP status: %d
            Original user request: "%s"
            Intent: %s
            Slot names (use these exact names for requestSlotCorrection): %s
            Collected values: %s

            Raw API response:
            %s
            """.formatted(
                outcome,
                result.statusCode(),
                ctx.getInput(),
                ctx.getIntentSlug(),
                slotSummary,
                slotValues,
                result.body() != null ? result.body() : (result.errorMessage() != null ? result.errorMessage() : "(empty)")
        );
    }
}
