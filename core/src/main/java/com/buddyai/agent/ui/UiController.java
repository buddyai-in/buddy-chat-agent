package com.buddyai.agent.ui;

import com.buddyai.agent.entity.AgentChatLog;
import com.buddyai.agent.entity.AgentSession;
import com.buddyai.agent.mcp.McpClientRegistry;
import com.buddyai.agent.repository.AgentChatLogRepository;
import com.buddyai.agent.repository.AgentSessionRepository;
import com.buddyai.agent.service.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MVC controller that serves all Thymeleaf UI pages.
 *
 * <p>Every handler method populates a {@link Model} with the data each
 * template needs and then returns the logical view name. The actual HTML
 * rendering is delegated to Thymeleaf.
 *
 * <p>Authentication for {@code /ui/**} paths is enforced by the inline
 * session-checking {@code HandlerInterceptor} registered in
 * {@link com.buddyai.agent.config.WebConfig}.
 */
@Controller
@RequestMapping("/ui")
@Slf4j
@RequiredArgsConstructor
public class UiController {

    private final AgentSessionRepository sessionRepository;
    private final AgentChatLogRepository chatLogRepository;
    private final McpClientRegistry mcpClientRegistry;
    private final MetricsService metricsService;

    // -------------------------------------------------------------------------
    // Dashboard
    // -------------------------------------------------------------------------

    /**
     * Landing page – shows a high-level operational summary.
     */
    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("stats", buildDashboardStats());
        model.addAttribute("mcpServers", buildMcpServerList());
        model.addAttribute("recentLogs", chatLogRepository.findTop10ByOrderByCreatedAtDesc());
        return "pages/dashboard";
    }

    // -------------------------------------------------------------------------
    // Playground
    // -------------------------------------------------------------------------

    /**
     * Interactive chat playground for ad-hoc agent testing.
     */
    @GetMapping("/playground")
    public String playground(Model model) {
        model.addAttribute("defaultClientId", "default");
        return "pages/playground";
    }

    // -------------------------------------------------------------------------
    // Services
    // -------------------------------------------------------------------------

    /**
     * Services catalogue page. The template fetches live data from
     * {@code /ui/api/services} via JavaScript; this handler only seeds the
     * model with the URL and any filter state coming from the query string.
     */
    @GetMapping("/services")
    public String services(
            Model model,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page) {

        model.addAttribute("search", search);
        model.addAttribute("page", page);
        model.addAttribute("apiServiceUrl", "/ui/api/services");
        return "pages/services";
    }

    // -------------------------------------------------------------------------
    // Databases
    // -------------------------------------------------------------------------

    @GetMapping("/databases")
    public String databases(Model model) {
        return "pages/databases";
    }

    // -------------------------------------------------------------------------
    // Documents
    // -------------------------------------------------------------------------

    @GetMapping("/documents")
    public String documents(Model model) {
        return "pages/documents";
    }

    // -------------------------------------------------------------------------
    // Sessions
    // -------------------------------------------------------------------------

    /**
     * Paginated session browser with optional status filter.
     */
    @GetMapping("/sessions")
    public String sessions(
            Model model,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String status) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AgentSession> sessions;
        if (status.isBlank()) {
            sessions = sessionRepository.findAll(pageable);
        } else {
            sessions = sessionRepository.findByState(status, pageable);
        }

        model.addAttribute("sessions", sessions);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", sessions.getTotalPages());
        model.addAttribute("statusFilter", status);
        return "pages/sessions";
    }

    // -------------------------------------------------------------------------
    // Chat Logs
    // -------------------------------------------------------------------------

    /**
     * Paginated chat-log browser with optional status and keyword filters.
     */
    @GetMapping("/logs")
    public String logs(
            Model model,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String search) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AgentChatLog> logs;
        if (!status.isBlank()) {
            logs = chatLogRepository.findByStatus(status, pageable);
        } else {
            logs = chatLogRepository.findAll(pageable);
        }

        model.addAttribute("logs", logs);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", logs.getTotalPages());
        model.addAttribute("statusFilter", status);
        model.addAttribute("search", search);
        return "pages/logs";
    }

    // -------------------------------------------------------------------------
    // Memory
    // -------------------------------------------------------------------------

    /**
     * Memory explorer – filter by userId and/or category.
     */
    @GetMapping("/memory")
    public String memory(
            Model model,
            @RequestParam(defaultValue = "") String userId,
            @RequestParam(defaultValue = "") String category) {

        model.addAttribute("userId", userId);
        model.addAttribute("category", category);
        return "pages/memory";
    }

    // -------------------------------------------------------------------------
    // Settings
    // -------------------------------------------------------------------------

    @GetMapping("/settings")
    public String settings(Model model) {
        return "pages/settings";
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Build the aggregate stats map displayed on the dashboard.
     *
     * <p>Combines repository counts with the Micrometer-based
     * {@link MetricsService} for the global "all" pseudo-client.
     */
    private Map<String, Object> buildDashboardStats() {
        long total = chatLogRepository.count();
        long success = chatLogRepository.countByStatus("SUCCESS");
        long toolCount = mcpClientRegistry.getRegisteredToolNames().size();
        long activeSessions = sessionRepository.countByState("ACTIVE");

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMessages", total);
        stats.put("successCount", success);
        stats.put("errorCount", total - success);
        stats.put("mcpTools", toolCount);
        stats.put("activeSessions", activeSessions);
        stats.put("successRate", total > 0 ? (double) success / total * 100 : 0.0);
        return stats;
    }

    /**
     * Build the list of known MCP servers with their approximate connectivity
     * status derived from the registered tool set.
     *
     * <p>Since {@link McpClientRegistry} does not expose per-server grouping
     * beyond tool names, connectivity is approximated as {@code true} when at
     * least one tool has been registered, and tool counts are distributed
     * evenly across the declared server list.
     */
    private List<Map<String, Object>> buildMcpServerList() {
        Set<String> toolNames = mcpClientRegistry.getRegisteredToolNames();
        boolean anyConnected = !toolNames.isEmpty();
        int totalTools = toolNames.size();

        String[][] serverDefs = {
            {"api-mcp-server",          ":8081"},
            {"document-mcp-server",     ":8082"},
            {"database-mcp-server",     ":8083"},
            {"speech-mcp-server",       ":8084"},
            {"memory-mcp-server",       ":8085"},
            {"notification-mcp-server", ":8086"}
        };

        List<Map<String, Object>> servers = new ArrayList<>();
        int serverCount = serverDefs.length;
        for (String[] def : serverDefs) {
            Map<String, Object> s = new HashMap<>();
            s.put("name", def[0]);
            s.put("port", def[1]);
            s.put("connected", anyConnected);
            s.put("toolCount", totalTools / Math.max(1, serverCount));
            servers.add(s);
        }
        return servers;
    }
}
