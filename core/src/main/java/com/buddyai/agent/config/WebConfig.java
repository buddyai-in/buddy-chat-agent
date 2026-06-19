package com.buddyai.agent.config;

import com.buddyai.agent.interceptor.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

/**
 * Spring MVC configuration: API authentication interceptor, UI session-based
 * authentication, CORS mappings, and static resource handling.
 *
 * <p>Two interceptors are registered:
 * <ol>
 *   <li>{@link AuthInterceptor} — validates the {@code X-Client-Id} /
 *       {@code X-Secret-Key} headers on every {@code /v1/agent/**} request
 *       (health endpoints are excluded).</li>
 *   <li>An inline {@link HandlerInterceptor} — guards {@code /ui/**} paths by
 *       checking for an {@code authenticated} flag in the HTTP session and
 *       redirecting unauthenticated visitors to {@code /ui/login}.</li>
 * </ol>
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    // -------------------------------------------------------------------------
    // Interceptors
    // -------------------------------------------------------------------------

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // --- API Bearer-token / secret-key auth ---
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/v1/agent/**")
                .excludePathPatterns("/v1/agent/*/health");

        // --- UI session auth (inline, no extra Spring bean needed) ---
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(
                    HttpServletRequest req,
                    HttpServletResponse res,
                    Object handler) throws Exception {

                String path = req.getRequestURI();

                // Always allow the login page, static assets, and actuator
                if (path.startsWith("/ui/login")
                        || path.startsWith("/static")
                        || path.startsWith("/manage")) {
                    return true;
                }

                // All other /ui/** paths require an authenticated session
                if (path.startsWith("/ui")) {
                    Object authenticated = req.getSession(false) != null
                            ? req.getSession(false).getAttribute("authenticated")
                            : null;
                    if (authenticated == null || !(Boolean) authenticated) {
                        res.sendRedirect("/ui/login");
                        return false;
                    }
                }

                return true;
            }
        }).addPathPatterns("/ui/**");
    }

    // -------------------------------------------------------------------------
    // CORS
    // -------------------------------------------------------------------------

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/v1/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }

    // -------------------------------------------------------------------------
    // Static resources
    // -------------------------------------------------------------------------

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }
}
