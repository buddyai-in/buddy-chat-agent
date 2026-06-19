package com.buddyai.agent.response;

import com.buddyai.agent.enums.ResponseOutcome;
import com.buddyai.agent.pipeline.dto.ApiResult;
import org.springframework.stereotype.Component;

/**
 * Deterministic pre-step that maps an {@link ApiResult} to a
 * {@link ResponseOutcome}.
 *
 * <p>This keeps the LLM out of the "did it succeed?" decision — by the time
 * the {@code ResponseAgent} runs, the outcome is already known and only the
 * <em>presentation</em> is left to reasoning.</p>
 */
@Component
public class ResponseClassifier {

    public ResponseOutcome classify(ApiResult result) {
        if (result == null || !result.reachable()) {
            return ResponseOutcome.UNREACHABLE;
        }
        int code = result.statusCode();
        if (code >= 200 && code < 300) {
            return ResponseOutcome.OK;
        }
        if (code >= 400 && code < 500) {
            return ResponseOutcome.CLIENT_ERROR;
        }
        if (code >= 500) {
            return ResponseOutcome.SERVER_ERROR;
        }
        // 1xx/3xx or anything unexpected — treat as a server-side anomaly.
        return ResponseOutcome.SERVER_ERROR;
    }
}
