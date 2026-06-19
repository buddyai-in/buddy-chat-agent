package com.buddyai.agent.agent.orchestrator;

import com.buddyai.agent.dto.AgentRequest;
import com.buddyai.agent.dto.AgentResponse;
import com.buddyai.agent.memory.EpisodicMemoryStore;
import com.buddyai.agent.mcp.McpClientRegistry;
import com.buddyai.agent.pipeline.DecisionProcessor;
import com.buddyai.agent.pipeline.dto.ConversationContext;
import com.buddyai.agent.pipeline.service.ConversationStateService;
import com.buddyai.agent.repository.ServiceIntentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Primary orchestrator that routes a user message through the intent/slot pipeline
 * and/or Claude with MCP tools.
 *
 * <p>Flow:
 * <ol>
 *   <li>Check for an active pipeline state (slot filling in progress) for the session.</li>
 *   <li>If active pipeline state exists → route to DecisionProcessor.</li>
 *   <li>If no active state, check if predefined intents exist for clientId.</li>
 *   <li>If intents exist, run intent identification. If matched → pipeline.</li>
 *   <li>If no intents or no match → fall through to MCP tools with ChatClient.</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorAgent {

    private static final int MEMORY_WINDOW = 20;

    private final ChatClient chatClient;
    private final EpisodicMemoryStore episodicMemoryStore;
    private final McpClientRegistry mcpClientRegistry;
    private final ConversationStateService conversationStateService;
    private final DecisionProcessor decisionProcessor;
    private final ServiceIntentRepository serviceIntentRepository;

    public AgentResponse chat(AgentRequest request) {
        long start = Instant.now().toEpochMilli();

        log.info("OrchestratorAgent.chat — session={} client={} user={} channel={}",
                request.sessionId(), request.clientId(), request.userId(), request.channel());

        // 1. Check for active pipeline state (e.g., mid slot-filling)
        Optional<ConversationContext> activeState = conversationStateService.loadState(
                request.sessionId(), request.clientId());

        if (activeState.isPresent()) {
            ConversationContext ctx = activeState.get();
            ctx.setInput(request.message());
            ctx.setUserId(request.userId());
            if (request.metadata() != null) {
                if (ctx.getMetadata() == null) ctx.setMetadata(new java.util.HashMap<>());
                ctx.getMetadata().putAll(request.metadata());
            }

            decisionProcessor.process(ctx);

            // Persist state if pipeline is still in progress
            if (ctx.getState() != null && ctx.getState().name().contains("INQUIRY")) {
                conversationStateService.saveState(ctx);
            } else {
                conversationStateService.completeState(ctx.getRequestId());
            }

            if (ctx.getResponseText() != null) {
                long durationMs = Instant.now().toEpochMilli() - start;
                return buildPipelineResponse(ctx, request, durationMs);
            }
            // null responseText means fall-through to MCP (INTENT_NOT_FOUND)
        }

        // 2. No active state — check if predefined intents exist
        boolean hasIntents = !serviceIntentRepository.findByClientId(request.clientId()).isEmpty();

        if (hasIntents) {
            ConversationContext ctx = conversationStateService.buildContext(request);
            decisionProcessor.process(ctx);

            if (ctx.getResponseText() != null) {
                // Save state if we're in slot filling mode
                if (ctx.getState() != null && ctx.getState().name().contains("INQUIRY")) {
                    conversationStateService.saveState(ctx);
                }
                long durationMs = Instant.now().toEpochMilli() - start;
                return buildPipelineResponse(ctx, request, durationMs);
            }
            // Intent not found — fall through to MCP tools
        }

        // 3. Fall-through: use MCP tools + ChatClient
        return callWithMcpTools(request, start);
    }

    private AgentResponse callWithMcpTools(AgentRequest request, long start) {
        String systemPrompt = buildSystemPrompt(request);

        ToolCallback[] tools = mcpClientRegistry.getAllToolCallbacks();
        log.debug("Attaching {} MCP tool(s) to this request", tools.length);

        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(episodicMemoryStore)
                .conversationId(request.sessionId())
                .build();

        ChatResponse chatResponse = chatClient.prompt()
                .system(systemPrompt)
                .user(request.message())
                .advisors(memoryAdvisor)
                .tools(tools)
                .call()
                .chatResponse();

        String replyText = extractText(chatResponse);
        List<AgentResponse.ToolCall> toolCalls = extractToolCalls(chatResponse);

        long durationMs = Instant.now().toEpochMilli() - start;
        log.info("OrchestratorAgent completed in {}ms — toolCalls={}", durationMs, toolCalls.size());

        return AgentResponse.builder()
                .message(replyText)
                .sessionId(request.sessionId())
                .agentUsed("OrchestratorAgent")
                .toolCallsUsed(toolCalls)
                .durationMs(durationMs)
                .metadata(Map.of(
                        "clientId", request.clientId(),
                        "userId",   request.userId(),
                        "channel",  request.channel() != null ? request.channel() : "UNKNOWN"
                ))
                .build();
    }

    private AgentResponse buildPipelineResponse(ConversationContext ctx, AgentRequest request, long durationMs) {
        log.info("Pipeline response in {}ms — state={}", durationMs, ctx.getState());
        return AgentResponse.builder()
                .message(ctx.getResponseText())
                .sessionId(request.sessionId())
                .agentUsed("PipelineAgent")
                .toolCallsUsed(List.of())
                .durationMs(durationMs)
                .metadata(Map.of(
                        "clientId",   request.clientId(),
                        "userId",     request.userId(),
                        "channel",    request.channel() != null ? request.channel() : "UNKNOWN",
                        "state",      ctx.getState() != null ? ctx.getState().name() : "UNKNOWN",
                        "intentSlug", ctx.getIntentSlug() != null ? ctx.getIntentSlug() : ""
                ))
                .build();
    }

    private String buildSystemPrompt(AgentRequest request) {
        return """
                You are a helpful AI assistant for client '%s'.
                You are speaking with user '%s' via the '%s' channel.
                Answer concisely and helpfully. When you need external information or need to perform
                actions, use the available tools. Always prefer tool results over your training data
                when the tool can provide fresh, accurate information.
                """.formatted(
                request.clientId(),
                request.userId(),
                request.channel() != null ? request.channel() : "UNKNOWN"
        );
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null) return "";
        var output = response.getResult().getOutput();
        return output instanceof AssistantMessage am ? am.getText() : "";
    }

    private List<AgentResponse.ToolCall> extractToolCalls(ChatResponse response) {
        if (response == null || response.getResult() == null) return List.of();
        var output = response.getResult().getOutput();
        if (!(output instanceof AssistantMessage am)) return List.of();

        return am.getToolCalls().stream()
                .map(tc -> {
                    String mcpServer = mcpClientRegistry.getClientForTool(tc.name())
                            .map(Object::toString)
                            .orElse("unknown");
                    return AgentResponse.ToolCall.builder()
                            .toolName(tc.name())
                            .mcpServer(mcpServer)
                            .succeeded(true)
                            .build();
                })
                .toList();
    }
}
