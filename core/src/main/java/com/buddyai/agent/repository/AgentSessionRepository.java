package com.buddyai.agent.repository;

import com.buddyai.agent.entity.AgentSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access layer for {@link AgentSession} entities.
 *
 * <p>Spring Data JPA derives all queries from method names; no custom
 * {@code @Query} annotations are required.
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
     * specific client tenant.
     *
     * @param clientId the tenant/client identifier
     * @param userId   the end-user identifier
     * @param pageable pagination and sorting parameters
     * @return page of matching sessions
     */
    Page<AgentSession> findByClientIdAndUserId(String clientId, String userId, Pageable pageable);

    /**
     * Return a paginated list of sessions whose lifecycle state matches the
     * supplied value (e.g. {@code ACTIVE}, {@code COMPLETED}, {@code EXPIRED}).
     *
     * @param state    the lifecycle state to filter by
     * @param pageable pagination and sorting parameters
     * @return page of matching sessions
     */
    Page<AgentSession> findByState(String state, Pageable pageable);

    /**
     * Return a paginated list of all sessions belonging to a specific client tenant.
     *
     * @param clientId the tenant identifier
     * @param pageable pagination and sorting parameters
     * @return page of matching sessions
     */
    Page<AgentSession> findByClientId(String clientId, Pageable pageable);

    /**
     * Count all sessions in the given lifecycle state.
     *
     * @param state the lifecycle state (e.g. {@code ACTIVE})
     * @return number of matching sessions
     */
    long countByState(String state);

    /**
     * Return the ten most recently created sessions, newest first.
     * Useful for dashboard quick-view widgets.
     *
     * @return list of up to ten sessions ordered by {@code createdAt} descending
     */
    List<AgentSession> findTop10ByOrderByCreatedAtDesc();
}
