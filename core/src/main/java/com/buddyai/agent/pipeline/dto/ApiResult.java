package com.buddyai.agent.pipeline.dto;

/**
 * Outcome of an HTTP call made by {@code ApiExecutionService}.
 *
 * <p>Captures enough to classify the response deterministically: the HTTP
 * status code, the raw response body, and — when the call never completed —
 * a transport-level error flag and message.</p>
 *
 * @param statusCode   HTTP status (0 when the call never reached the server)
 * @param body         raw response body (may be {@code null})
 * @param reachable    {@code false} when a transport error prevented a response
 * @param errorMessage transport/error detail, if any
 */
public record ApiResult(int statusCode, String body, boolean reachable, String errorMessage) {

    public static ApiResult of(int statusCode, String body) {
        return new ApiResult(statusCode, body, true, null);
    }

    public static ApiResult unreachable(String errorMessage) {
        return new ApiResult(0, null, false, errorMessage);
    }

    public boolean isSuccess() {
        return reachable && statusCode >= 200 && statusCode < 300;
    }
}
