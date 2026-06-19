package com.buddyai.agent.repository;

import com.buddyai.agent.entity.Rule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleRepository extends JpaRepository<Rule, Long> {

    List<Rule> findByServiceParameterIdOrderByPriorityAsc(Long serviceParameterId);
}
