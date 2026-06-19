package com.buddyai.agent.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that aggregates all {@link McpSyncClient} instances present in the
 * application context and exposes their tools as Spring AI {@link ToolCallback}s.
 *
 * <p>Each MCP server registered as a Spring bean contributes one
 * {@link McpSyncClient}. At start-up the registry discovers every tool offered
 * by every server and builds two lookup maps:
 * <ul>
 *   <li>{@code toolName → ToolCallback} — for fast callback lookup</li>
 *   <li>{@code toolName → McpSyncClient} — for direct client access</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class McpClientRegistry {

    /** All MCP clients injected by Spring (one per configured MCP server). */
    private final List<McpSyncClient> mcpClients;

    /** Flat map of every discovered tool callback, keyed by tool name. */
    private final Map<String, ToolCallback> toolCallbackMap = new ConcurrentHashMap<>();

    /** Reverse map: tool name → the client that owns it. */
    private final Map<String, McpSyncClient> toolClientMap = new ConcurrentHashMap<>();

    public McpClientRegistry(List<McpSyncClient> mcpClients) {
        this.mcpClients = mcpClients != null ? mcpClients : List.of();
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Discover tools from all registered MCP servers at application start-up.
     * Logs a summary of each server and its available tools.
     */
    @PostConstruct
    public void discoverTools() {
        log.info("Initialising McpClientRegistry with {} MCP client(s)", mcpClients.size());

        for (McpSyncClient client : mcpClients) {
            try {
                McpSchema.ListToolsResult result = client.listTools();
                List<McpSchema.Tool> tools = result.tools();

                log.info("MCP server '{}' exposes {} tool(s):", serverName(client), tools.size());
                for (McpSchema.Tool tool : tools) {
                    SyncMcpToolCallback callback = new SyncMcpToolCallback(client, tool);
                    toolCallbackMap.put(tool.name(), callback);
                    toolClientMap.put(tool.name(), client);
                    log.info("  [{}] {}", tool.name(), tool.description());
                }
            } catch (Exception ex) {
                log.error("Failed to list tools for MCP client {}: {}", serverName(client), ex.getMessage(), ex);
            }
        }

        log.info("McpClientRegistry ready — {} tool(s) available across all servers",
                toolCallbackMap.size());
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Return all discovered tool callbacks as an array suitable for passing to
     * {@code ChatClient.Builder#defaultTools(ToolCallback...)}.
     *
     * @return array of all tool callbacks (never {@code null})
     */
    public ToolCallback[] getAllToolCallbacks() {
        return toolCallbackMap.values().toArray(new ToolCallback[0]);
    }

    /**
     * Return all discovered tool callbacks as a list.
     *
     * @return mutable snapshot of all available callbacks
     */
    public List<ToolCallback> getAllToolCallbackList() {
        return new ArrayList<>(toolCallbackMap.values());
    }

    /**
     * Look up a single tool callback by name.
     *
     * @param toolName the exact tool name as advertised by the MCP server
     * @return the matching callback, or empty if unknown
     */
    public Optional<ToolCallback> getToolCallback(String toolName) {
        return Optional.ofNullable(toolCallbackMap.get(toolName));
    }

    /**
     * Return the MCP client that owns a given tool.
     *
     * @param toolName the exact tool name
     * @return the owning client, or empty if unknown
     */
    public Optional<McpSyncClient> getClientForTool(String toolName) {
        return Optional.ofNullable(toolClientMap.get(toolName));
    }

    /**
     * Return the names of all registered tools.
     *
     * @return immutable set of tool names
     */
    public java.util.Set<String> getRegisteredToolNames() {
        return java.util.Collections.unmodifiableSet(toolCallbackMap.keySet());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String serverName(McpSyncClient client) {
        try {
            McpSchema.ServerCapabilities caps = client.getServerCapabilities();
            return caps != null ? caps.toString() : client.toString();
        } catch (Exception ex) {
            return client.toString();
        }
    }
}
