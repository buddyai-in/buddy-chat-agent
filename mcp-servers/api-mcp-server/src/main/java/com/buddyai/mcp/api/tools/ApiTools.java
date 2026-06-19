package com.buddyai.mcp.api.tools;

import com.buddyai.mcp.api.entity.ApiService;
import com.buddyai.mcp.api.repository.ApiServiceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
public class ApiTools {

    private final RestTemplate restTemplate;
    private final ApiServiceRepository apiServiceRepository;
    private final ObjectMapper objectMapper;

    public ApiTools(RestTemplate restTemplate,
                    ApiServiceRepository apiServiceRepository,
                    ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.apiServiceRepository = apiServiceRepository;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "Call a registered API service by name with given parameters. Returns the API response as JSON string.")
    public String callService(
            @ToolParam(description = "The service name to call") String serviceName,
            @ToolParam(description = "JSON string of parameters to pass") String parametersJson,
            @ToolParam(description = "The client ID making the request") String clientId
    ) {
        try {
            Optional<ApiService> serviceOpt = apiServiceRepository.findByNameAndClientId(serviceName, clientId);
            if (serviceOpt.isEmpty()) {
                return errorJson("Service not found: " + serviceName + " for client: " + clientId);
            }
            ApiService service = serviceOpt.get();
            if (!service.isEnabled()) {
                return errorJson("Service is disabled: " + serviceName);
            }

            JsonNode params = objectMapper.readTree(parametersJson);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            if ("API_KEY".equalsIgnoreCase(service.getAuthType()) && service.getApiKey() != null) {
                headers.set("X-API-Key", service.getApiKey());
            } else if ("BEARER".equalsIgnoreCase(service.getAuthType()) && service.getApiKey() != null) {
                headers.set("Authorization", "Bearer " + service.getApiKey());
            }

            String url = service.getEndpoint();
            HttpMethod method = HttpMethod.valueOf(service.getMethod().toUpperCase());

            HttpEntity<String> entity;
            if (method == HttpMethod.GET || method == HttpMethod.DELETE) {
                StringBuilder queryString = new StringBuilder("?");
                Iterator<Map.Entry<String, JsonNode>> fields = params.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    queryString.append(entry.getKey()).append("=").append(entry.getValue().asText()).append("&");
                }
                if (queryString.length() > 1) {
                    url = url + queryString.substring(0, queryString.length() - 1);
                }
                entity = new HttpEntity<>(headers);
            } else {
                entity = new HttpEntity<>(parametersJson, headers);
            }

            ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);

            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", response.getStatusCode().value());
            result.put("success", response.getStatusCode().is2xxSuccessful());
            try {
                result.set("data", objectMapper.readTree(response.getBody()));
            } catch (Exception e) {
                result.put("data", response.getBody());
            }
            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            return errorJson("Error calling service '" + serviceName + "': " + e.getMessage());
        }
    }

    @Tool(description = "List all available API services for a client. Returns service names and their descriptions.")
    public String listServices(
            @ToolParam(description = "The client ID") String clientId
    ) {
        try {
            List<ApiService> services = apiServiceRepository.findByClientIdAndEnabled(clientId, true);
            ArrayNode array = objectMapper.createArrayNode();
            for (ApiService svc : services) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("name", svc.getName());
                node.put("description", svc.getDescription() != null ? svc.getDescription() : "");
                node.put("method", svc.getMethod());
                node.put("endpoint", svc.getEndpoint());
                array.add(node);
            }
            ObjectNode result = objectMapper.createObjectNode();
            result.put("clientId", clientId);
            result.put("count", services.size());
            result.set("services", array);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return errorJson("Error listing services: " + e.getMessage());
        }
    }

    @Tool(description = "Get the input schema and required parameters for a specific API service.")
    public String getServiceSchema(
            @ToolParam(description = "Service name") String serviceName,
            @ToolParam(description = "Client ID") String clientId
    ) {
        try {
            Optional<ApiService> serviceOpt = apiServiceRepository.findByNameAndClientId(serviceName, clientId);
            if (serviceOpt.isEmpty()) {
                return errorJson("Service not found: " + serviceName);
            }
            ApiService service = serviceOpt.get();

            ObjectNode result = objectMapper.createObjectNode();
            result.put("serviceName", service.getName());
            result.put("endpoint", service.getEndpoint());
            result.put("method", service.getMethod());
            result.put("description", service.getDescription() != null ? service.getDescription() : "");
            result.put("authType", service.getAuthType() != null ? service.getAuthType() : "NONE");

            if (service.getRequestSchema() != null && !service.getRequestSchema().isBlank()) {
                try {
                    result.set("requestSchema", objectMapper.readTree(service.getRequestSchema()));
                } catch (Exception e) {
                    result.put("requestSchema", service.getRequestSchema());
                }
            } else {
                result.set("requestSchema", objectMapper.createObjectNode());
            }

            if (service.getResponseSchema() != null && !service.getResponseSchema().isBlank()) {
                try {
                    result.set("responseSchema", objectMapper.readTree(service.getResponseSchema()));
                } catch (Exception e) {
                    result.put("responseSchema", service.getResponseSchema());
                }
            } else {
                result.set("responseSchema", objectMapper.createObjectNode());
            }

            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return errorJson("Error getting schema for service '" + serviceName + "': " + e.getMessage());
        }
    }

    @Tool(description = "Validate that all required parameters for a service are present before calling.")
    public String validateServiceParameters(
            @ToolParam(description = "Service name") String serviceName,
            @ToolParam(description = "JSON string of provided parameters") String parametersJson,
            @ToolParam(description = "Client ID") String clientId
    ) {
        try {
            Optional<ApiService> serviceOpt = apiServiceRepository.findByNameAndClientId(serviceName, clientId);
            if (serviceOpt.isEmpty()) {
                return errorJson("Service not found: " + serviceName);
            }
            ApiService service = serviceOpt.get();

            JsonNode provided = objectMapper.readTree(parametersJson);
            List<String> missingParams = new ArrayList<>();
            List<String> invalidParams = new ArrayList<>();

            if (service.getRequestSchema() != null && !service.getRequestSchema().isBlank()) {
                JsonNode schema = objectMapper.readTree(service.getRequestSchema());
                JsonNode properties = schema.get("properties");
                JsonNode required = schema.get("required");

                if (required != null && required.isArray()) {
                    for (JsonNode req : required) {
                        String paramName = req.asText();
                        if (!provided.has(paramName) || provided.get(paramName).isNull()) {
                            missingParams.add(paramName);
                        }
                    }
                }

                if (properties != null) {
                    Iterator<Map.Entry<String, JsonNode>> fields = provided.fields();
                    while (fields.hasNext()) {
                        String paramName = fields.next().getKey();
                        if (!properties.has(paramName)) {
                            invalidParams.add(paramName);
                        }
                    }
                }
            }

            ObjectNode result = objectMapper.createObjectNode();
            result.put("valid", missingParams.isEmpty());
            result.put("serviceName", serviceName);

            ArrayNode missingArray = objectMapper.createArrayNode();
            missingParams.forEach(missingArray::add);
            result.set("missingParams", missingArray);

            ArrayNode invalidArray = objectMapper.createArrayNode();
            invalidParams.forEach(invalidArray::add);
            result.set("invalidParams", invalidArray);

            if (!missingParams.isEmpty()) {
                result.put("message", "Missing required parameters: " + String.join(", ", missingParams));
            } else {
                result.put("message", "All required parameters are present");
            }

            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return errorJson("Error validating parameters: " + e.getMessage());
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
