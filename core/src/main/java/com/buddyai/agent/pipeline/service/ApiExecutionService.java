package com.buddyai.agent.pipeline.service;

import com.buddyai.agent.entity.IntegratedService;
import com.buddyai.agent.pipeline.dto.ApiResult;
import com.buddyai.agent.repository.IntegratedServiceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiExecutionService {

    private final IntegratedServiceRepository integratedServiceRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ApiResult execute(Long serviceId, Map<String, String> params) {
        IntegratedService svc = integratedServiceRepository.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("IntegratedService not found: " + serviceId));

        String url = svc.getEndpoint();
        if (url != null && params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry.getValue() != null) {
                    url = url.replace("{" + entry.getKey() + "}", entry.getValue());
                }
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (svc.getHeaders() != null && !svc.getHeaders().isBlank()) {
            try {
                Map<String, String> extraHeaders = objectMapper.readValue(
                        svc.getHeaders(), new TypeReference<Map<String, String>>() {});
                extraHeaders.forEach(headers::set);
            } catch (Exception e) {
                log.warn("Failed to parse headers JSON for service {}: {}", serviceId, e.getMessage());
            }
        }

        String method = svc.getMethod() != null ? svc.getMethod().toUpperCase() : "POST";

        try {
            ResponseEntity<String> response;
            if ("GET".equals(method)) {
                if (params != null && !params.isEmpty()) {
                    StringBuilder sb = new StringBuilder(url).append("?");
                    params.forEach((k, v) -> { if (v != null) sb.append(k).append("=").append(v).append("&"); });
                    url = sb.toString().replaceAll("&$", "");
                }
                response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            } else {
                Map<String, Object> body = new HashMap<>();
                if (params != null) body.putAll(params);
                String bodyJson = objectMapper.writeValueAsString(body);
                HttpEntity<String> entity = new HttpEntity<>(bodyJson, headers);
                response = restTemplate.exchange(url, HttpMethod.valueOf(method), entity, String.class);
            }
            return ApiResult.of(response.getStatusCode().value(), response.getBody());
        } catch (HttpStatusCodeException e) {
            log.warn("API call returned error status {} for service {}", e.getStatusCode(), serviceId);
            return ApiResult.of(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            log.error("API unreachable for service {}: {}", serviceId, e.getMessage());
            return ApiResult.unreachable(e.getMessage());
        } catch (Exception e) {
            log.error("API call failed for service {}: {}", serviceId, e.getMessage(), e);
            return ApiResult.unreachable(e.getMessage());
        }
    }
}
