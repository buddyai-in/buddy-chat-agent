package com.buddyai.agent.interceptor;

import com.buddyai.agent.entity.ClientAuth;
import com.buddyai.agent.repository.ClientAuthRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * Spring MVC interceptor that validates Bearer token credentials for every
 * request arriving at the {@code /v1/agent/**} path pattern.
 *
 * <p>Requests to management, UI, static, and health endpoints are always allowed
 * through without authentication checks.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final ClientAuthRepository clientAuthRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String path = request.getRequestURI();

        // Allow management, UI, static assets, and health checks without auth
        if (path.startsWith("/manage")
                || path.startsWith("/ui")
                || path.startsWith("/static")
                || path.contains("/health")) {
            return true;
        }

        // Require Bearer token
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            log.warn("Missing or malformed Authorization header for path={}", path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Missing or invalid Authorization header\"}");
            return false;
        }

        String token = auth.substring(7);
        String clientId = extractClientId(request);

        Optional<ClientAuth> clientAuth = clientAuthRepository.findByClientIdAndEnabled(clientId, true);
        if (clientAuth.isEmpty() || !clientAuth.get().getSecretKey().equals(token)) {
            log.warn("Invalid credentials for clientId={} path={}", clientId, path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid credentials\"}");
            return false;
        }

        log.debug("Authenticated clientId={} path={}", clientId, path);
        return true;
    }

    /**
     * Extract the {@code {clientId}} path variable from the request URI.
     * Expected pattern: {@code /v1/agent/{clientId}/...}
     *
     * @param request the current HTTP request
     * @return the client identifier, or an empty string if it cannot be derived
     */
    private String extractClientId(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Strip context path if present
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty()) {
            path = path.substring(contextPath.length());
        }
        String[] parts = path.split("/");
        // Expected segments: ["", "v1", "agent", "{clientId}", ...]
        return parts.length > 3 ? parts[3] : "";
    }
}
