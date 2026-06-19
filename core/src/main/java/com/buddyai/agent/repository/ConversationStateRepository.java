package com.buddyai.agent.repository;

import com.buddyai.agent.entity.ConversationState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationStateRepository extends JpaRepository<ConversationState, String> {

    Optional<ConversationState> findBySessionIdAndCompleted(String sessionId, Boolean completed);
}
