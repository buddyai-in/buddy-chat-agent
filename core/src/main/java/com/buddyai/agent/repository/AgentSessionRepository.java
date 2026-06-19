package com.buddyai.agent.repository;

import com.buddyai.agent.entity.AgentSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access layer for {@link AgentSession} entities.
 */
@Repository
public interface AgentSessionRepository extends JpaRepository<AgentSession, Long> {

    /**
     * Locate a session by its external session identifier.
     *
     * @param sessionId the UUID or opaque string that identifies the session
     * @return the matching session, or empty if none exists
     */
    Optional<AgentSession> findBySessionId(String sessionId);

    /**
     * Return a paginated list of sessions belonging to a specific user within a
     * specific client tenant, ordered by the database default (typically by id).
     *
     * @param clientId the tenant/client identifier
     * @param userId   the end-user identifier
     * @param pageable pagination and sorting parameters
     * @return page of matching sessions
     */
    Page<AgentSession> findByClientIdAndUserId(String clientId, String userId, Pageable pageable);
}
