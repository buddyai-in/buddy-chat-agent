package com.buddyai.agent.repository;

import com.buddyai.agent.entity.ServiceIntent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceIntentRepository extends JpaRepository<ServiceIntent, Long> {

    List<ServiceIntent> findByClientId(String clientId);

    Optional<ServiceIntent> findByClientIdAndIntentSlug(String clientId, String intentSlug);
}
