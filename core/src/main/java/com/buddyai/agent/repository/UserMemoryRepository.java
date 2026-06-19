package com.buddyai.agent.repository;

import com.buddyai.agent.entity.UserMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Data access layer for {@link UserMemory} entities (semantic memory).
 */
@Repository
public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    /**
     * Return all memory facts for a user within a specific client tenant.
     *
     * @param userId   the end-user identifier
     * @param clientId the tenant/client identifier
     * @return all matching memory rows (unsorted)
     */
    List<UserMemory> findByUserIdAndClientId(String userId, String clientId);

    /**
     * Return all memory facts for a user that belong to a given category.
     * Useful for targeted retrieval, e.g. fetching only {@code PREFERENCE} facts.
     *
     * @param userId   the end-user identifier
     * @param category one of {@code PREFERENCE}, {@code HISTORY}, {@code PROFILE},
     *                 {@code CONTEXT}
     * @return matching memory rows (unsorted)
     */
    List<UserMemory> findByUserIdAndCategory(String userId, String category);
}
