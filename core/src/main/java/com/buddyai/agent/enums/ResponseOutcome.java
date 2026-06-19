package com.buddyai.agent.enums;

/**
 * Deterministic classification of an upstream API response.
 *
 * <p>Computed once by {@code ResponseClassifier} and passed into the
 * {@code ResponseAgent} prompt so the LLM never has to guess whether a
 * call succeeded — it only decides <em>how to present</em> the outcome.</p>
 */
public enum ResponseOutcome {

    /** 2xx — the call succeeded. */
    OK,

    /** 4xx — the request was rejected (bad/missing input). User can correct. */
    CLIENT_ERROR,

    /** 5xx — the upstream service failed. Not the user's fault. */
    SERVER_ERROR,

    /** Transport failure — the service could not be reached at all. */
    UNREACHABLE
}
