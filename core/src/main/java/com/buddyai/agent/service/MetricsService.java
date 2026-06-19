package com.buddyai.agent.service;

import com.buddyai.agent.dto.AgentRequest;
import com.buddyai.agent.dto.AgentResponse;
import com.buddyai.agent.repository.AgentChatLogRepository;
import com.buddyai.agent.repository.AgentSessionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Records operational metrics for every chat interaction and exposes a
 * dashboard summary endpoint.
 *
 * <p>Micrometer meters registered here:
 * <ul>
 *   <li>{@code chat_requests_total} – counter tagged with {@code clientId} and {@code status}</li>
 *   <li>{@code chat_duration_seconds} – timer tagged with {@code clientId} and {@code agentUsed}</li>
 *   <li>{@code active_sessions_total} – gauge reflecting live session count</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MetricsService {

    private final AgentChatLogRepository chatLogRepository;
    private final AgentSessionRepository sessionRepository;
    private final MeterRegistry meterRegistry;

    /**
     * Record metrics for a completed chat turn.
     *
     * @param request  the inbound {@link AgentRequest}
     * @param response the outbound {@link AgentResponse}
     * @param status   {@code SUCCESS} or {@code ERROR}
     */
    public void recordChat(AgentRequest request, AgentResponse response, String status) {
        // Increment the request counter
        Counter.builder("chat_requests_total")
                .description("Total number of chat requests handled")
                .tag("clientId", request.clientId() != null ? request.clientId() : "unknown")
                .tag("status", status)
                .register(meterRegistry)
                .increment();

        // Record the response time
        String agentUsed = response != null && response.agentUsed() != null ? response.agentUsed() : "unknown";
        Timer.builder("chat_duration_seconds")
                .description("Time taken to process a chat request")
                .tag("clientId", request.clientId() != null ? request.clientId() : "unknown")
                .tag("agentUsed", agentUsed)
                .register(meterRegistry)
                .record(response != null ? response.durationMs() : 0L, TimeUnit.MILLISECONDS);

        // Register an active-sessions gauge lazily (refreshed each call)
        AtomicLong activeSessionsHolder = new AtomicLong(sessionRepository.count());
        meterRegistry.gauge("active_sessions_total",
                activeSessionsHolder,
                AtomicLong::get);
    }

    /**
     * Build a dashboard summary for a client tenant.
     *
     * @param clientId tenant identifier
     * @return map containing {@code totalMessages}, {@code successRate}, {@code avgDurationMs},
     *         {@code todayMessages}, {@code activeSessions}
     */
    public Map<String, Object> getDashboardStats(String clientId) {
        long successCount = chatLogRepository.countByClientIdAndStatus(clientId, "SUCCESS");
        long errorCount = chatLogRepository.countByClientIdAndStatus(clientId, "ERROR");
        long totalMessages = successCount + errorCount;

        double successRate = totalMessages > 0 ? (double) successCount / totalMessages : 0.0;

        LocalDateTime startOfToday = LocalDateTime.now().toLocalDate().atStartOfDay();
        long todayMessages = chatLogRepository.countByClientIdAndCreatedAtAfter(clientId, startOfToday);

        // Approximate average duration from the Timer registered in Micrometer
        double avgDurationMs = 0.0;
        try {
            Timer timer = meterRegistry.find("chat_duration_seconds")
                    .tag("clientId", clientId)
                    .timer();
            if (timer != null && timer.count() > 0) {
                avgDurationMs = timer.mean(TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            log.debug("Could not retrieve duration timer for clientId={}", clientId, e);
        }

        long activeSessions = sessionRepository.findByClientId(clientId, Pageable.unpaged()).getTotalElements();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMessages", totalMessages);
        stats.put("successRate", successRate);
        stats.put("avgDurationMs", avgDurationMs);
        stats.put("todayMessages", todayMessages);
        stats.put("activeSessions", activeSessions);
        return stats;
    }
}
