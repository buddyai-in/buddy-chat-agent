package com.buddyai.agent.repository;

import com.buddyai.agent.entity.ConversationTurn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data access layer for {@link ConversationTurn} entities (episodic memory).
 */
@Repository
public interface ConversationTurnRepository extends JpaRepository<ConversationTurn, Long> {

    /**
     * Return all turns for a session in strict chronological order.
     *
     * @param sessionId the owning session identifier
     * @return ordered list of turns, oldest first
     */
    List<ConversationTurn> findBySessionIdOrderBySequenceNumAsc(String sessionId);

    /**
     * Return the N most-recent turns for a session (descending sequence, then
     * reversed in the service layer so the list is chronological).
     *
     * @param sessionId the owning session identifier
     * @param limit     maximum number of turns to retrieve
     * @return at most {@code limit} turns, newest first
     */
    @Query("""
            SELECT t FROM ConversationTurn t
            WHERE t.sessionId = :sessionId
            ORDER BY t.sequenceNum DESC
            LIMIT :limit
            """)
    List<ConversationTurn> findLastNBySessionId(
            @Param("sessionId") String sessionId,
            @Param("limit") int limit
    );

    /**
     * Prune old turns from a session that were created before the given cutoff.
     * Useful for rolling-window retention policies.
     *
     * @param sessionId the session to prune
     * @param cutoff    delete turns created strictly before this timestamp
     * @return the number of rows deleted
     */
    @Modifying
    @Transactional
    @Query("""
            DELETE FROM ConversationTurn t
            WHERE t.sessionId = :sessionId
              AND t.createdAt < :cutoff
            """)
    int deleteBySessionIdAndCreatedAtBefore(
            @Param("sessionId") String sessionId,
            @Param("cutoff") LocalDateTime cutoff
    );

    /**
     * Count the total number of turns stored for a session.
     *
     * @param sessionId the session identifier
     * @return total turn count
     */
    long countBySessionId(String sessionId);
}
