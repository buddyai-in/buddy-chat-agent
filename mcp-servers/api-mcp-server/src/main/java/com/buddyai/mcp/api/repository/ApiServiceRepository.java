package com.buddyai.mcp.api.repository;

import com.buddyai.mcp.api.entity.ApiService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiServiceRepository extends JpaRepository<ApiService, Long> {

    List<ApiService> findByClientId(String clientId);

    Optional<ApiService> findByNameAndClientId(String name, String clientId);

    List<ApiService> findByClientIdAndEnabled(String clientId, boolean enabled);
}
