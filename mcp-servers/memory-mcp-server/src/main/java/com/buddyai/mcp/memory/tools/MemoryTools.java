package com.buddyai.mcp.memory.tools;

import com.buddyai.mcp.memory.entity.UserMemory;
import com.buddyai.mcp.memory.repository.UserMemoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MemoryTools {

    private static final Set<String> VALID_CATEGORIES = Set.of("PREFERENCE", "HISTORY", "PROFILE", "CONTEXT");

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final UserMemoryRepository memoryRepository;
    private final ObjectMapper objectMapper;

    public MemoryTools(VectorStore vectorStore,
                       EmbeddingModel embeddingModel,
                       UserMemoryRepository memoryRepository,
                       ObjectMapper objectMapper) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.memoryRepository = memoryRepository;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "Store a fact or preference about a user for future recall across conversations.")
    public String rememberUserFact(
            @ToolParam(description = "User ID") String userId,
            @ToolParam(description = "The fact or preference to remember") String fact,
            @ToolParam(description = "Category: PREFERENCE, HISTORY, PROFILE, or CONTEXT") String category,
            @ToolParam(description = "Client ID") String clientId
    ) {
        try {
            String normalizedCategory = VALID_CATEGORIES.contains(category.toUpperCase())
                    ? category.toUpperCase()
                    : "CONTEXT";

            // Persist to relational DB for structured queries
            UserMemory memory = UserMemory.builder()
                    .userId(userId)
                    .clientId(clientId)
                    .fact(fact)
                    .category(normalizedCategory)
                    .build();
            UserMemory saved = memoryRepository.save(memory);

            // Also embed and store in vector store for semantic recall
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("userId", userId);
            metadata.put("clientId", clientId);
            metadata.put("category", normalizedCategory);
            metadata.put("memoryId", saved.getId().toString());
            metadata.put("createdAt", saved.getCreatedAt().toString());

            Document doc = new Document(fact, metadata);
            vectorStore.add(List.of(doc));

            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", "remembered");
            result.put("memoryId", saved.getId());
            result.put("userId", userId);
            result.put("category", normalizedCategory);
            result.put("fact", fact);
            result.put("createdAt", saved.getCreatedAt().toString());
            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            return errorJson("Error storing memory: " + e.getMessage());
        }
    }

    @Tool(description = "Recall relevant memories about a user based on the current conversation topic.")
    public String recallMemories(
            @ToolParam(description = "User ID") String userId,
            @ToolParam(description = "Current topic or query to find relevant memories for") String query,
            @ToolParam(description = "Client ID") String clientId,
            @ToolParam(description = "Max memories to return (default 5)") int maxResults
    ) {
        try {
            int topK = maxResults > 0 ? maxResults : 5;

            FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
            var filterExpression = filterBuilder
                    .and(
                            filterBuilder.eq("userId", userId),
                            filterBuilder.eq("clientId", clientId)
                    ).build();

            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .filterExpression(filterExpression)
                    .build();

            List<Document> results = vectorStore.similaritySearch(searchRequest);

            ArrayNode memories = objectMapper.createArrayNode();
            for (Document doc : results) {
                ObjectNode node = objectMapper.createObjectNode();
                Map<String, Object> meta = doc.getMetadata();
                node.put("memoryId", meta.getOrDefault("memoryId", "").toString());
                node.put("fact", doc.getText());
                node.put("category", meta.getOrDefault("category", "").toString());
                node.put("createdAt", meta.getOrDefault("createdAt", "").toString());
                node.put("relevanceScore", meta.containsKey("distance")
                        ? 1.0 - Double.parseDouble(meta.get("distance").toString()) : 0.0);
                memories.add(node);
            }

            ObjectNode result = objectMapper.createObjectNode();
            result.put("userId", userId);
            result.put("query", query);
            result.put("memoriesFound", results.size());
            result.set("memories", memories);
            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            return errorJson("Error recalling memories: " + e.getMessage());
        }
    }

    @Tool(description = "Get a user's profile summary including their preferences and interaction history.")
    public String getUserProfile(
            @ToolParam(description = "User ID") String userId,
            @ToolParam(description = "Client ID") String clientId
    ) {
        try {
            List<UserMemory> allMemories = memoryRepository.findByUserIdAndClientId(userId, clientId);

            ObjectNode profile = objectMapper.createObjectNode();
            profile.put("userId", userId);
            profile.put("clientId", clientId);
            profile.put("totalMemories", allMemories.size());

            // Group by category
            Map<String, List<String>> byCategory = new LinkedHashMap<>();
            for (String cat : VALID_CATEGORIES) {
                byCategory.put(cat, new ArrayList<>());
            }

            for (UserMemory mem : allMemories) {
                byCategory.computeIfAbsent(mem.getCategory(), k -> new ArrayList<>())
                          .add(mem.getFact());
            }

            ObjectNode categories = objectMapper.createObjectNode();
            for (Map.Entry<String, List<String>> entry : byCategory.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    ArrayNode factsArray = objectMapper.createArrayNode();
                    entry.getValue().forEach(factsArray::add);
                    categories.set(entry.getKey(), factsArray);
                }
            }
            profile.set("categories", categories);

            // Find most recent memory
            allMemories.stream()
                    .max(Comparator.comparing(UserMemory::getCreatedAt))
                    .ifPresent(mem -> profile.put("lastMemoryAt", mem.getCreatedAt().toString()));

            return objectMapper.writeValueAsString(profile);

        } catch (Exception e) {
            return errorJson("Error getting user profile: " + e.getMessage());
        }
    }

    @Tool(description = "Forget a specific memory or clear all memories for a user.")
    public String forgetMemory(
            @ToolParam(description = "User ID") String userId,
            @ToolParam(description = "Memory ID to forget (or 'all' to clear all memories)") String memoryIdOrAll,
            @ToolParam(description = "Client ID") String clientId
    ) {
        try {
            if ("all".equalsIgnoreCase(memoryIdOrAll)) {
                // Delete all from relational DB
                memoryRepository.deleteAllByUserIdAndClientId(userId, clientId);

                // Delete all from vector store
                FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
                var filterExpression = filterBuilder
                        .and(
                                filterBuilder.eq("userId", userId),
                                filterBuilder.eq("clientId", clientId)
                        ).build();
                vectorStore.delete(filterExpression);

                ObjectNode result = objectMapper.createObjectNode();
                result.put("status", "cleared");
                result.put("userId", userId);
                result.put("message", "All memories cleared for user " + userId);
                return objectMapper.writeValueAsString(result);

            } else {
                long memoryId;
                try {
                    memoryId = Long.parseLong(memoryIdOrAll);
                } catch (NumberFormatException e) {
                    return errorJson("Invalid memory ID: " + memoryIdOrAll + ". Use a numeric ID or 'all'");
                }

                Optional<UserMemory> memOpt = memoryRepository.findById(memoryId);
                if (memOpt.isEmpty()) {
                    return errorJson("Memory not found with ID: " + memoryId);
                }

                UserMemory mem = memOpt.get();
                if (!mem.getUserId().equals(userId) || !mem.getClientId().equals(clientId)) {
                    return errorJson("Memory does not belong to the specified user/client");
                }

                memoryRepository.deleteById(memoryId);

                // Delete from vector store by memoryId metadata filter
                FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
                var filterExpression = filterBuilder
                        .eq("memoryId", String.valueOf(memoryId)).build();
                vectorStore.delete(filterExpression);

                ObjectNode result = objectMapper.createObjectNode();
                result.put("status", "forgotten");
                result.put("memoryId", memoryId);
                result.put("userId", userId);
                result.put("fact", mem.getFact());
                return objectMapper.writeValueAsString(result);
            }

        } catch (Exception e) {
            return errorJson("Error forgetting memory: " + e.getMessage());
        }
    }

    private String errorJson(String message) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("error", true);
            node.put("message", message);
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            return "{\"error\":true,\"message\":\"" + message.replace("\"", "'") + "\"}";
        }
    }
}
