package com.buddyai.mcp.database.repository;

import com.buddyai.mcp.database.entity.DatabaseConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DatabaseConnectionRepository extends JpaRepository<DatabaseConnection, Long> {

    List<DatabaseConnection> findByClientId(String clientId);

    Optional<DatabaseConnection> findByIdAndClientId(Long id, String clientId);

    List<DatabaseConnection> findByClientIdAndEnabled(String clientId, boolean enabled);
}
