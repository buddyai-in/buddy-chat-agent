package com.buddyai.agent.endpoint;

import com.buddyai.agent.entity.AgentChatLog;
import com.buddyai.agent.service.ChatLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoints for browsing chat logs and retrieving usage statistics.
 */
@RestController
@RequestMapping("/v1/agent")
@Slf4j
@RequiredArgsConstructor
public class LogEndpoint {

    private final ChatLogService chatLogService;

    /**
     * Return a paginated list of chat log entries for a client, newest first.
     *
     * @param clientId tenant identifier
     * @param page     zero-based page index (default 0)
     * @param size     page size (default 20)
     * @return page of {@link AgentChatLog}
     */
    @GetMapping("/{clientId}/logs")
    public ResponseEntity<Page<AgentChatLog>> getLogs(
            @PathVariable String clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AgentChatLog> logs = chatLogService.getLogs(
                clientId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(logs);
    }

    /**
     * Return aggregated statistics for a client tenant.
     *
     * @param clientId tenant identifier
     * @return map with {@code totalMessages}, {@code successCount}, {@code errorCount},
     *         {@code todayMessages}
     */
    @GetMapping("/{clientId}/stats")
    public ResponseEntity<Map<String, Object>> getStats(@PathVariable String clientId) {
        return ResponseEntity.ok(chatLogService.getStats(clientId));
    }
}
