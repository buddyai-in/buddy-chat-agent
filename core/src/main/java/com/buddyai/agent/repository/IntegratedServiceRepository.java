package com.buddyai.agent.repository;

import com.buddyai.agent.entity.IntegratedService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IntegratedServiceRepository extends JpaRepository<IntegratedService, Long> {

    List<IntegratedService> findByClientIdAndEnabled(String clientId, Boolean enabled);
}
