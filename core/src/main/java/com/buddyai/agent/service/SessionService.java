package com.buddyai.agent.service;

import com.buddyai.agent.entity.AgentSession;
import com.buddyai.agent.repository.AgentSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Manages the lifecycle of {@link AgentSession} records.
 * Sessions are created on first contact and closed either explicitly or by TTL.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SessionService {

    private final AgentSessionRepository sessionRepository;

    /**
     * Create a brand-new session for the given client/user/channel combination.
     *
     * @param clientId tenant identifier
     * @param userId   end-user identifier
     * @param channel  originating channel (REST, TELEGRAM, …)
     * @return the persisted {@link AgentSession}
     */
    @Transactional
    public AgentSession createSession(String clientId, String userId, String channel) {
        String sessionId = UUID.randomUUID().toString();
        AgentSession session = AgentSession.builder()
                .sessionId(sessionId)
                .clientId(clientId)
                .userId(userId)
                .channel(channel)
                .state("ACTIVE")
                .build();
        AgentSession saved = sessionRepository.save(session);
        log.info("Created session {} for client={} user={} channel={}", sessionId, clientId, userId, channel);
        return saved;
    }

    /**
     * Look up an existing session by its identifier.
     *
     * @param sessionId the session UUID
     * @return the session wrapped in {@link Optional}, or empty if not found
     */
    public Optional<AgentSession> getSession(String sessionId) {
        return sessionRepository.findBySessionId(sessionId);
    }

    /**
     * Return an existing session or create a new one if none exists.
     * Useful for channel webhooks (Telegram, WhatsApp) that supply a stable chat ID.
     *
     * @param sessionId the desired session identifier
     * @param clientId  tenant identifier (used only when creating a new session)
     * @param userId    end-user identifier (used only when creating a new session)
     * @param channel   originating channel (used only when creating a new session)
     * @return existing or newly created {@link AgentSession}
     */
    @Transactional
    public AgentSession getOrCreateSession(String sessionId, String clientId, String userId, String channel) {
        return sessionRepository.findBySessionId(sessionId)
                .orElseGet(() -> {
                    log.info("No session found for id={}, creating new one", sessionId);
                    AgentSession session = AgentSession.builder()
                            .sessionId(sessionId)
                            .clientId(clientId)
                            .userId(userId)
                            .channel(channel)
                            .state("ACTIVE")
                            .build();
                    return sessionRepository.save(session);
                });
    }

    /**
     * Mark a session as {@code COMPLETED} so it is excluded from active lookups.
     *
     * @param sessionId the session to close
     */
    @Transactional
    public void closeSession(String sessionId) {
        sessionRepository.findBySessionId(sessionId).ifPresentOrElse(session -> {
            session.setState("COMPLETED");
            sessionRepository.save(session);
            log.info("Closed session {}", sessionId);
        }, () -> log.warn("closeSession called for unknown sessionId={}", sessionId));
    }

    /**
     * Return a paginated view of all sessions belonging to a client tenant.
     *
     * @param clientId tenant identifier
     * @param pageable pagination and sorting parameters
     * @return page of sessions
     */
    public Page<AgentSession> getClientSessions(String clientId, Pageable pageable) {
        return sessionRepository.findByClientId(clientId, pageable);
    }
}
