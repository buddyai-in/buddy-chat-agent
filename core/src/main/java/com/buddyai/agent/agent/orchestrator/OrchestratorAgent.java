package com.buddyai.agent.agent.orchestrator;

import com.buddyai.agent.dto.AgentRequest;
import com.buddyai.agent.dto.AgentResponse;
import com.buddyai.agent.memory.EpisodicMemoryStore;
import com.buddyai.agent.mcp.McpClientRegistry;
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

/**
 * Primary orchestrator that routes a user message through Claude,
 * episodic memory, and all registered MCP tools.
 *
 * <p>Flow:
 * <ol>
 *   <li>Build a system prompt contextualised with client / user / channel info.</li>
 *   <li>Attach a {@link MessageChatMemoryAdvisor} backed by {@link EpisodicMemoryStore}
 *       so Claude sees the recent conversation history.</li>
 *   <li>Register all MCP tool callbacks from {@link McpClientRegistry}.</li>
 *   <li>Call Claude; collect the response text and any tool calls made.</li>
 *   <li>Return a fully populated {@link AgentResponse}.</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorAgent {

    /** Number of recent turns injected into every Claude request. */
    private static final int MEMORY_WINDOW = 20;

    private final ChatClient chatClient;
    private final EpisodicMemoryStore episodicMemoryStore;
    private final McpClientRegistry mcpClientRegistry;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Process a single agent request end-to-end.
     *
     * @param request the inbound request from a channel adapter or REST endpoint
     * @return the agent's response, including timing and tool-call metadata
     */
    public AgentResponse chat(AgentRequest request) {
        long start = Instant.now().toEpochMilli();

        log.info("OrchestratorAgent.chat — session={} client={} user={} channel={}",
                request.sessionId(), request.clientId(), request.userId(), request.channel());

        String systemPrompt = buildSystemPrompt(request);

        // Collect tool callbacks from all MCP servers.
        ToolCallback[] tools = mcpClientRegistry.getAllToolCallbacks();
        log.debug("Attaching {} MCP tool(s) to this request", tools.length);

        // Build the memory advisor for this session.
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(episodicMemoryStore)
                .conversationId(request.sessionId())
                .build();

        // Call Claude.
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
                        "channel",  request.channel() != null ? request.channel().name() : "UNKNOWN"
                ))
                .build();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

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
                request.channel() != null ? request.channel().name() : "UNKNOWN"
        );
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return "";
        }
        var output = response.getResult().getOutput();
        return output instanceof AssistantMessage am ? am.getText() : "";
    }

    private List<AgentResponse.ToolCall> extractToolCalls(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return List.of();
        }
        var output = response.getResult().getOutput();
        if (!(output instanceof AssistantMessage am)) {
            return List.of();
        }

        return am.getToolCalls().stream()
                .map(tc -> {
                    // Determine which MCP server owns this tool (best-effort).
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
