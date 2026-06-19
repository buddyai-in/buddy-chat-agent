package com.buddyai.mcp.memory.repository;

import com.buddyai.mcp.memory.entity.UserMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    List<UserMemory> findByUserIdAndClientId(String userId, String clientId);

    List<UserMemory> findByUserIdAndClientIdAndCategory(String userId, String clientId, String category);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserMemory m WHERE m.userId = :userId AND m.clientId = :clientId")
    void deleteAllByUserIdAndClientId(@Param("userId") String userId, @Param("clientId") String clientId);
}
