package com.buddyai.agent.repository;

import com.buddyai.agent.entity.AgentChatLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data access layer for {@link AgentChatLog} entities.
 *
 * <p>Spring Data JPA derives all queries from method names; no custom
 * {@code @Query} annotations are required.
 */
@Repository
public interface AgentChatLogRepository extends JpaRepository<AgentChatLog, Long> {

    /**
     * Paginated list of logs for a client, newest first.
     *
     * @param clientId the tenant identifier
     * @param pageable pagination parameters
     * @return page of logs ordered by {@code createdAt} descending
     */
    Page<AgentChatLog> findByClientIdOrderByCreatedAtDesc(String clientId, Pageable pageable);

    /**
     * All log entries for a single session, in chronological order.
     *
     * @param sessionId the session identifier
     * @return list of logs ordered by {@code createdAt} ascending
     */
    List<AgentChatLog> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    /**
     * Paginated list of logs filtered by outcome status.
     *
     * @param status   {@code SUCCESS} or {@code ERROR}
     * @param pageable pagination and sorting parameters
     * @return page of matching logs
     */
    Page<AgentChatLog> findByStatus(String status, Pageable pageable);

    /**
     * Paginated list of logs for a client filtered by outcome status.
     *
     * @param clientId the tenant identifier
     * @param status   {@code SUCCESS} or {@code ERROR}
     * @param pageable pagination and sorting parameters
     * @return page of matching logs
     */
    Page<AgentChatLog> findByClientIdAndStatus(String clientId, String status, Pageable pageable);

    /**
     * Logs for a client within a time window, with pagination support.
     *
     * @param clientId the tenant identifier
     * @param from     lower bound (inclusive)
     * @param to       upper bound (inclusive)
     * @param pageable pagination parameters
     * @return page of logs within the requested window
     */
    Page<AgentChatLog> findByClientIdAndCreatedAtBetween(
            String clientId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    /**
     * Total number of entries for a client with a specific outcome status.
     *
     * @param clientId the tenant identifier
     * @param status   {@code SUCCESS} or {@code ERROR}
     * @return count of matching rows
     */
    long countByClientIdAndStatus(String clientId, String status);

    /**
     * Total number of entries for a client created after the given timestamp.
     *
     * @param clientId the tenant identifier
     * @param after    lower bound (exclusive)
     * @return count of matching rows
     */
    long countByClientIdAndCreatedAtAfter(String clientId, LocalDateTime after);

    /**
     * Total number of entries across all clients with a specific outcome status.
     *
     * @param status {@code SUCCESS} or {@code ERROR}
     * @return count of matching rows
     */
    long countByStatus(String status);

    /**
     * Return the ten most recently created chat log entries, newest first.
     * Used by the dashboard to show a recent-activity feed.
     *
     * @return list of up to ten log entries ordered by {@code createdAt} descending
     */
    List<AgentChatLog> findTop10ByOrderByCreatedAtDesc();
}
