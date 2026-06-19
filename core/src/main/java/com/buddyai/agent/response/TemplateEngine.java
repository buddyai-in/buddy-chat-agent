package com.buddyai.agent.response;

/**
 * Renders a response template against a JSON payload.
 *
 * <p>Used for the deterministic fast-path: when an intent/service defines a
 * fixed {@code botResponseTemplate}, the business wants exact wording, so we
 * render it directly instead of asking the LLM. Kept as an interface so the
 * templating implementation can evolve (handlebars, Mustache, etc.) without
 * touching callers.</p>
 */
public interface TemplateEngine {

    /**
     * @param template   the template text (may contain {@code {{path}}},
     *                   {@code {{#each list}}…{{/each}}}, {@code {{#if path}}…{{/if}}})
     * @param jsonBody   the raw JSON response to bind against
     * @return the rendered text, or the template unchanged if it has no tokens
     */
    String render(String template, String jsonBody);
}
