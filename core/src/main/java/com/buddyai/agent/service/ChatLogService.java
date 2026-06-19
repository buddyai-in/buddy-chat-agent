package com.buddyai.agent.service;

import com.buddyai.agent.dto.AgentRequest;
import com.buddyai.agent.dto.AgentResponse;
import com.buddyai.agent.entity.AgentChatLog;
import com.buddyai.agent.repository.AgentChatLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles persistence and retrieval of {@link AgentChatLog} records.
 * The primary write path ({@link #logAsync}) is executed on a background thread
 * so it never adds latency to the chat response.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatLogService {

    private final AgentChatLogRepository logRepository;

    /**
     * Persist a log entry asynchronously after a chat turn completes.
     * Failures are caught and logged so they never surface to the caller.
     *
     * @param request      the inbound {@link AgentRequest}
     * @param response     the outbound {@link AgentResponse} (may be {@code null} on error)
     * @param status       {@code SUCCESS} or {@code ERROR}
     * @param errorMessage human-readable error detail, or {@code null} on success
     */
    @Async("agentTaskExecutor")
    public void logAsync(AgentRequest request, AgentResponse response, String status, String errorMessage) {
        try {
            String toolsCalled = null;
            String agentUsed = null;
            Long durationMs = null;
            String agentResponse = null;

            if (response != null) {
                agentUsed = response.agentUsed();
                durationMs = response.durationMs();
                agentResponse = response.message();
                if (response.toolCallsUsed() != null && !response.toolCallsUsed().isEmpty()) {
                    toolsCalled = response.toolCallsUsed().stream()
                            .map(AgentResponse.ToolCall::toolName)
                            .collect(Collectors.joining(","));
                }
            }

            AgentChatLog chatLog = AgentChatLog.builder()
                    .sessionId(request.sessionId())
                    .clientId(request.clientId())
                    .userId(request.userId())
                    .channel(request.channel())
                    .userMessage(request.message())
                    .agentResponse(agentResponse)
                    .agentUsed(agentUsed)
                    .toolsCalled(toolsCalled)
                    .durationMs(durationMs)
                    .status(status)
                    .errorMessage(errorMessage)
                    .build();

            logRepository.save(chatLog);
            log.debug("Saved chat log for sessionId={} clientId={} status={}", request.sessionId(), request.clientId(), status);
        } catch (Exception e) {
            log.error("Failed to persist chat log for sessionId={}", request.sessionId(), e);
        }
    }

    /**
     * Paginated list of logs for a client, newest first.
     *
     * @param clientId tenant identifier
     * @param pageable pagination parameters
     * @return page of {@link AgentChatLog}
     */
    public Page<AgentChatLog> getLogs(String clientId, Pageable pageable) {
        return logRepository.findByClientIdOrderByCreatedAtDesc(clientId, pageable);
    }

    /**
     * All log entries for a session in chronological order.
     *
     * @param sessionId the session identifier
     * @return ordered list of log entries
     */
    public List<AgentChatLog> getSessionLogs(String sessionId) {
        return logRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * Compute summary statistics for a client tenant.
     *
     * @param clientId tenant identifier
     * @return map containing {@code totalMessages}, {@code successCount}, {@code errorCount},
     *         {@code todayMessages}
     */
    public Map<String, Object> getStats(String clientId) {
        long totalMessages = logRepository.countByClientIdAndStatus(clientId, "SUCCESS")
                + logRepository.countByClientIdAndStatus(clientId, "ERROR");
        long successCount = logRepository.countByClientIdAndStatus(clientId, "SUCCESS");
        long errorCount = logRepository.countByClientIdAndStatus(clientId, "ERROR");
        LocalDateTime startOfToday = LocalDateTime.now().toLocalDate().atStartOfDay();
        long todayMessages = logRepository.countByClientIdAndCreatedAtAfter(clientId, startOfToday);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMessages", totalMessages);
        stats.put("successCount", successCount);
        stats.put("errorCount", errorCount);
        stats.put("todayMessages", todayMessages);
        return stats;
    }
}
