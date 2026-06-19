package com.buddyai.agent.endpoint;

import com.buddyai.agent.agent.orchestrator.OrchestratorAgent;
import com.buddyai.agent.dto.AgentRequest;
import com.buddyai.agent.dto.AgentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST entry-point for the agent chat API.
 *
 * <h3>Endpoints</h3>
 * <pre>
 * POST /v1/agent/{clientId}/chat
 *   Headers: X-Session-Id (optional), X-User-Id, X-Channel
 *   Body:    { "message": "..." }
 *   Returns: AgentResponse (JSON)
 *
 * GET  /v1/agent/{clientId}/health
 *   Returns: { "status": "UP", "clientId": "..." }
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/v1/agent")
@RequiredArgsConstructor
public class ChatEndpoint {

    private final OrchestratorAgent orchestratorAgent;

    // -------------------------------------------------------------------------
    // Chat
    // -------------------------------------------------------------------------

    /**
     * Process a user chat message and return the agent's reply.
     *
     * @param clientId  path variable — the tenant identifier
     * @param sessionId optional {@code X-Session-Id} header; a new UUID is minted when absent
     * @param userId    {@code X-User-Id} header — the end-user identifier
     * @param channel   {@code X-Channel} header — one of REST / TELEGRAM / WHATSAPP / TEAMS
     * @param body      request body containing the {@code message} field
     * @return the agent response as JSON
     */
    @PostMapping(
            value = "/{clientId}/chat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AgentResponse> chat(
            @PathVariable String clientId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
            @RequestHeader(value = "X-Channel", defaultValue = "REST") String channel,
            @RequestBody Map<String, Object> body
    ) {
        String resolvedSessionId = (sessionId != null && !sessionId.isBlank())
                ? sessionId
                : UUID.randomUUID().toString();

        String message = extractMessage(body);

        AgentRequest.Channel channelEnum = parseChannel(channel);

        log.info("POST /{}/chat — session={} user={} channel={}", clientId, resolvedSessionId, userId, channelEnum);

        AgentRequest request = AgentRequest.builder()
                .message(message)
                .sessionId(resolvedSessionId)
                .clientId(clientId)
                .userId(userId)
                .channel(channelEnum)
                .metadata(Map.copyOf(body))
                .build();

        AgentResponse response = orchestratorAgent.chat(request);
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // Health
    // -------------------------------------------------------------------------

    /**
     * Lightweight health check for this client tenant.
     *
     * @param clientId the tenant identifier
     * @return {@code {"status":"UP","clientId":"..."}}
     */
    @GetMapping(value = "/{clientId}/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> health(@PathVariable String clientId) {
        return ResponseEntity.ok(Map.of(
                "status",   "UP",
                "clientId", clientId
        ));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String extractMessage(Map<String, Object> body) {
        Object value = body.get("message");
        if (value == null) {
            throw new IllegalArgumentException("Request body must contain a 'message' field");
        }
        return value.toString();
    }

    private AgentRequest.Channel parseChannel(String channel) {
        try {
            return AgentRequest.Channel.valueOf(channel.toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown channel '{}', defaulting to REST", channel);
            return AgentRequest.Channel.REST;
        }
    }
}
