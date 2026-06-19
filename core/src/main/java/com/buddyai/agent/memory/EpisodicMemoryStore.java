package com.buddyai.agent.memory;

import com.buddyai.agent.entity.ConversationTurn;
import com.buddyai.agent.repository.ConversationTurnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Episodic memory store backed by PostgreSQL via {@link ConversationTurnRepository}.
 *
 * <p>Implements Spring AI's {@link ChatMemory} interface so it can be plugged directly
 * into a {@code MessageChatMemoryAdvisor}. Each Spring AI {@link Message} is persisted
 * as a {@link ConversationTurn} row; on retrieval the rows are converted back into
 * typed Spring AI message objects.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EpisodicMemoryStore implements ChatMemory {

    private static final String ROLE_USER      = "USER";
    private static final String ROLE_ASSISTANT = "ASSISTANT";
    private static final String ROLE_TOOL      = "TOOL";
    private static final String ROLE_SYSTEM    = "SYSTEM";

    private final ConversationTurnRepository turnRepository;

    // -------------------------------------------------------------------------
    // ChatMemory interface
    // -------------------------------------------------------------------------

    /**
     * Persist a list of messages for the given conversation ID (session ID).
     *
     * @param conversationId the session / conversation identifier
     * @param messages       the messages to append
     */
    @Override
    @Transactional
    public void add(String conversationId, List<Message> messages) {
        long next = turnRepository.countBySessionId(conversationId) + 1;
        for (Message message : messages) {
            String role    = roleOf(message);
            String content = message.getText();
            String toolName = extractToolName(message);

            ConversationTurn turn = ConversationTurn.builder()
                    .sessionId(conversationId)
                    .role(role)
                    .content(content)
                    .toolName(toolName)
                    .sequenceNum((int) next++)
                    .build();

            turnRepository.save(turn);
            log.debug("Persisted {} turn #{} for session {}", role, turn.getSequenceNum(), conversationId);
        }
    }

    /**
     * Retrieve the most recent {@code lastN} messages for a conversation.
     * Passing {@link Integer#MAX_VALUE} returns the full history.
     *
     * @param conversationId the session / conversation identifier
     * @param lastN          maximum number of messages to return (newest first in DB, reversed here)
     * @return chronologically ordered list of Spring AI messages
     */
    @Override
    @Transactional(readOnly = true)
    public List<Message> get(String conversationId, int lastN) {
        List<ConversationTurn> turns;
        if (lastN == Integer.MAX_VALUE) {
            turns = turnRepository.findBySessionIdOrderBySequenceNumAsc(conversationId);
        } else {
            // Repository returns newest-first; reverse to restore chronological order.
            turns = turnRepository.findLastNBySessionId(conversationId, lastN);
            turns = turns.reversed();
        }

        log.debug("Loaded {} turns for session {}", turns.size(), conversationId);
        return turns.stream()
                .map(this::toMessage)
                .collect(Collectors.toList());
    }

    /**
     * Delete all turns belonging to the specified conversation.
     *
     * @param conversationId the session / conversation identifier
     */
    @Override
    @Transactional
    public void clear(String conversationId) {
        List<ConversationTurn> all = turnRepository.findBySessionIdOrderBySequenceNumAsc(conversationId);
        turnRepository.deleteAll(all);
        log.info("Cleared {} turns for session {}", all.size(), conversationId);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private String roleOf(Message message) {
        return switch (message.getMessageType()) {
            case USER      -> ROLE_USER;
            case ASSISTANT -> ROLE_ASSISTANT;
            case TOOL      -> ROLE_TOOL;
            case SYSTEM    -> ROLE_SYSTEM;
        };
    }

    /**
     * Extract the tool name from an assistant message that represents a tool call,
     * or from a tool-result message. Returns {@code null} for ordinary messages.
     */
    private String extractToolName(Message message) {
        if (message instanceof AssistantMessage am) {
            return am.getToolCalls().stream()
                    .findFirst()
                    .map(tc -> tc.name())
                    .orElse(null);
        }
        return null;
    }

    private Message toMessage(ConversationTurn turn) {
        return switch (turn.getRole()) {
            case ROLE_USER      -> new UserMessage(turn.getContent());
            case ROLE_ASSISTANT -> new AssistantMessage(turn.getContent());
            case ROLE_SYSTEM    -> new SystemMessage(turn.getContent());
            // TOOL turns are surfaced as assistant messages to keep the history readable.
            default             -> new AssistantMessage("[tool:" + turn.getToolName() + "] " + turn.getContent());
        };
    }
}
