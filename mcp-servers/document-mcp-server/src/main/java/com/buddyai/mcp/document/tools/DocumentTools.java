package com.buddyai.mcp.document.tools;

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

import java.time.Instant;
import java.util.*;

@Component
public class DocumentTools {

    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private static final int DEFAULT_CHUNK_OVERLAP = 100;

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;

    public DocumentTools(VectorStore vectorStore,
                         EmbeddingModel embeddingModel,
                         ObjectMapper objectMapper) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "Search through uploaded documents using semantic similarity. Returns relevant document chunks with their source.")
    public String searchDocuments(
            @ToolParam(description = "The search query or question") String query,
            @ToolParam(description = "Client ID to scope the search") String clientId,
            @ToolParam(description = "Maximum number of results to return (default 5)") int maxResults
    ) {
        try {
            int topK = maxResults > 0 ? maxResults : 5;

            FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
            var filterExpression = filterBuilder.eq("clientId", clientId).build();

            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .filterExpression(filterExpression)
                    .build();

            List<Document> results = vectorStore.similaritySearch(searchRequest);

            ArrayNode array = objectMapper.createArrayNode();
            for (Document doc : results) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("content", doc.getText());
                Map<String, Object> meta = doc.getMetadata();
                node.put("source", meta.getOrDefault("documentName", "unknown").toString());
                node.put("category", meta.getOrDefault("category", "").toString());
                node.put("score", meta.containsKey("distance")
                        ? 1.0 - Double.parseDouble(meta.get("distance").toString()) : 0.0);
                ObjectNode metaNode = objectMapper.createObjectNode();
                meta.forEach((k, v) -> metaNode.put(k, v != null ? v.toString() : ""));
                node.set("metadata", metaNode);
                array.add(node);
            }

            ObjectNode result = objectMapper.createObjectNode();
            result.put("query", query);
            result.put("clientId", clientId);
            result.put("resultCount", results.size());
            result.set("results", array);
            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            return errorJson("Error searching documents: " + e.getMessage());
        }
    }

    @Tool(description = "Upload and index a document for future semantic search. Accepts text content.")
    public String indexDocument(
            @ToolParam(description = "Document text content to index") String content,
            @ToolParam(description = "Document name or identifier") String documentName,
            @ToolParam(description = "Client ID") String clientId,
            @ToolParam(description = "Category or tag for filtering") String category
    ) {
        try {
            List<String> chunks = splitIntoChunks(content, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
            List<Document> documents = new ArrayList<>();
            String indexedAt = Instant.now().toString();

            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("documentName", documentName);
                metadata.put("clientId", clientId);
                metadata.put("category", category != null ? category : "general");
                metadata.put("chunkIndex", i);
                metadata.put("totalChunks", chunks.size());
                metadata.put("indexedAt", indexedAt);

                Document doc = new Document(chunks.get(i), metadata);
                documents.add(doc);
            }

            vectorStore.add(documents);

            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", "indexed");
            result.put("documentName", documentName);
            result.put("clientId", clientId);
            result.put("category", category != null ? category : "general");
            result.put("chunksCreated", chunks.size());
            result.put("indexedAt", indexedAt);
            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            return errorJson("Error indexing document '" + documentName + "': " + e.getMessage());
        }
    }

    @Tool(description = "List all indexed documents for a client.")
    public String listDocuments(
            @ToolParam(description = "Client ID") String clientId
    ) {
        try {
            // Search with a broad query to get all documents for this client
            FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
            var filterExpression = filterBuilder.eq("clientId", clientId).build();

            // Use a generic query with high topK to discover distinct documents
            SearchRequest searchRequest = SearchRequest.builder()
                    .query("document")
                    .topK(500)
                    .filterExpression(filterExpression)
                    .build();

            List<Document> allDocs = vectorStore.similaritySearch(searchRequest);

            // Aggregate by documentName
            Map<String, ObjectNode> docMap = new LinkedHashMap<>();
            for (Document doc : allDocs) {
                Map<String, Object> meta = doc.getMetadata();
                String docName = meta.getOrDefault("documentName", "unknown").toString();
                if (!docMap.containsKey(docName)) {
                    ObjectNode node = objectMapper.createObjectNode();
                    node.put("documentName", docName);
                    node.put("category", meta.getOrDefault("category", "").toString());
                    node.put("indexedAt", meta.getOrDefault("indexedAt", "").toString());
                    node.put("chunkCount", 0);
                    docMap.put(docName, node);
                }
                ObjectNode existing = docMap.get(docName);
                existing.put("chunkCount", existing.get("chunkCount").asInt() + 1);
            }

            ArrayNode array = objectMapper.createArrayNode();
            docMap.values().forEach(array::add);

            ObjectNode result = objectMapper.createObjectNode();
            result.put("clientId", clientId);
            result.put("documentCount", docMap.size());
            result.set("documents", array);
            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            return errorJson("Error listing documents: " + e.getMessage());
        }
    }

    @Tool(description = "Delete a document and all its indexed chunks from the knowledge base.")
    public String deleteDocument(
            @ToolParam(description = "Document name to delete") String documentName,
            @ToolParam(description = "Client ID") String clientId
    ) {
        try {
            FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
            var filterExpression = filterBuilder
                    .and(
                            filterBuilder.eq("documentName", documentName),
                            filterBuilder.eq("clientId", clientId)
                    ).build();

            vectorStore.delete(filterExpression);

            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", "deleted");
            result.put("documentName", documentName);
            result.put("clientId", clientId);
            result.put("message", "Document and all its chunks have been removed from the knowledge base");
            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            return errorJson("Error deleting document '" + documentName + "': " + e.getMessage());
        }
    }

    private List<String> splitIntoChunks(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += chunkSize - overlap;
            if (start >= text.length()) break;
        }
        return chunks;
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
