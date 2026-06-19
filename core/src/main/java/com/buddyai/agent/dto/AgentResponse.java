package com.buddyai.agent.dto;

import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Outbound response returned to the calling channel.
 */
@Builder
public record AgentResponse(
        String message,
        String sessionId,
        List<ToolCall> toolCallsUsed,
        String agentUsed,
        long durationMs,
        Map<String, Object> metadata
) {

    /**
     * Lightweight description of a single tool invocation included in the response
     * so that callers can audit what the agent did.
     */
    @Builder
    public record ToolCall(
            String toolName,
            String mcpServer,
            boolean succeeded
    ) {}
}
