package com.buddyai.agent.repository;

import com.buddyai.agent.entity.ServiceParameter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceParameterRepository extends JpaRepository<ServiceParameter, Long> {

    List<ServiceParameter> findByServiceIdOrderBySequenceAsc(Long serviceId);

    List<ServiceParameter> findByServiceIdAndRequired(Long serviceId, Boolean required);
}
