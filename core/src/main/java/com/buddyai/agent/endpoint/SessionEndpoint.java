package com.buddyai.agent.endpoint;

import com.buddyai.agent.entity.AgentSession;
import com.buddyai.agent.entity.ConversationTurn;
import com.buddyai.agent.repository.ConversationTurnRepository;
import com.buddyai.agent.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for managing agent sessions and retrieving conversation history.
 */
@RestController
@RequestMapping("/v1/agent")
@Slf4j
@RequiredArgsConstructor
public class SessionEndpoint {

    private final SessionService sessionService;
    private final ConversationTurnRepository conversationTurnRepository;

    /**
     * Create a new session for the specified client.
     *
     * @param clientId tenant identifier (path variable)
     * @param userId   end-user identifier from {@code X-User-Id} header
     * @param channel  originating channel from {@code X-Channel} header
     * @return the newly created {@link AgentSession}
     */
    @PostMapping("/{clientId}/sessions")
    public ResponseEntity<AgentSession> createSession(
            @PathVariable String clientId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
            @RequestHeader(value = "X-Channel", defaultValue = "REST") String channel) {

        AgentSession session = sessionService.createSession(clientId, userId, channel);
        log.info("Created session {} for clientId={}", session.getSessionId(), clientId);
        return ResponseEntity.ok(session);
    }

    /**
     * Retrieve a specific session, ensuring it belongs to the given client.
     *
     * @param clientId  tenant identifier
     * @param sessionId session UUID
     * @return the session, or 404 if not found or ownership mismatch
     */
    @GetMapping("/{clientId}/sessions/{sessionId}")
    public ResponseEntity<AgentSession> getSession(
            @PathVariable String clientId,
            @PathVariable String sessionId) {

        return sessionService.getSession(sessionId)
                .filter(s -> s.getClientId().equals(clientId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Close (mark as COMPLETED) a session owned by the given client.
     *
     * @param clientId  tenant identifier
     * @param sessionId session UUID
     * @return 204 No Content
     */
    @DeleteMapping("/{clientId}/sessions/{sessionId}")
    public ResponseEntity<Void> closeSession(
            @PathVariable String clientId,
            @PathVariable String sessionId) {

        sessionService.closeSession(sessionId);
        log.info("Closed session {} for clientId={}", sessionId, clientId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Return the full ordered conversation history for a session.
     *
     * @param clientId  tenant identifier (used for ownership validation)
     * @param sessionId session UUID
     * @return list of {@link ConversationTurn} in chronological order, or 404
     */
    @GetMapping("/{clientId}/sessions/{sessionId}/history")
    public ResponseEntity<List<ConversationTurn>> getHistory(
            @PathVariable String clientId,
            @PathVariable String sessionId) {

        // Validate that the session belongs to this client before returning history
        return sessionService.getSession(sessionId)
                .filter(s -> s.getClientId().equals(clientId))
                .map(s -> {
                    List<ConversationTurn> turns =
                            conversationTurnRepository.findBySessionIdOrderBySequenceNumAsc(sessionId);
                    return ResponseEntity.ok(turns);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
