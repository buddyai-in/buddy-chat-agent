package com.buddyai.agent.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compact handlebars-style {@link TemplateEngine} backed by Jackson.
 *
 * <p>Supports the subset that covers the vast majority of bot-response
 * templates from the original chat-assistant:</p>
 * <ul>
 *   <li>{@code {{path.to.value}}} — dotted-path variable substitution</li>
 *   <li>{@code {{#each items}} … {{this}} / {{field}} … {{/each}}} — iteration</li>
 *   <li>{@code {{#if path}} … {{/if}}} — truthy conditional blocks</li>
 * </ul>
 *
 * <p>Processing order matters: blocks ({@code #each}, {@code #if}) are resolved
 * before simple variables so inner tokens bind against the right scope.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JacksonTemplateEngine implements TemplateEngine {

    private static final Pattern EACH = Pattern.compile("\\{\\{#each\\s+([^}]+)}}(.*?)\\{\\{/each}}", Pattern.DOTALL);
    private static final Pattern IF = Pattern.compile("\\{\\{#if\\s+([^}]+)}}(.*?)\\{\\{/if}}", Pattern.DOTALL);
    private static final Pattern VAR = Pattern.compile("\\{\\{\\s*([^}#/][^}]*?)\\s*}}");

    private final ObjectMapper objectMapper;

    @Override
    public String render(String template, String jsonBody) {
        if (template == null || template.isBlank()) {
            return template;
        }
        JsonNode root;
        try {
            root = (jsonBody == null || jsonBody.isBlank())
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(jsonBody);
        } catch (Exception e) {
            log.warn("Template render: body is not valid JSON, returning template verbatim: {}", e.getMessage());
            return template;
        }
        return process(template, root);
    }

    private String process(String template, JsonNode scope) {
        String out = processEach(template, scope);
        out = processIf(out, scope);
        out = processVars(out, scope);
        return out;
    }

    private String processEach(String template, JsonNode scope) {
        Matcher m = EACH.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            JsonNode array = resolve(scope, m.group(1).trim());
            String body = m.group(2);
            StringBuilder rendered = new StringBuilder();
            if (array != null && array.isArray()) {
                for (JsonNode item : array) {
                    // Each item becomes the scope; full processing binds {{this}},
                    // {{field}} and nested blocks against the item.
                    rendered.append(process(body, item));
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rendered.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String processIf(String template, JsonNode scope) {
        Matcher m = IF.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            JsonNode val = resolve(scope, m.group(1).trim());
            String replacement = isTruthy(val) ? processVars(m.group(2), scope) : "";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String processVars(String template, JsonNode scope) {
        Matcher m = VAR.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String path = m.group(1).trim();
            JsonNode val = "this".equals(path) ? scope : resolve(scope, path);
            String replacement = (val == null || val.isNull()) ? ""
                    : val.isValueNode() ? val.asText() : val.toString();
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private JsonNode resolve(JsonNode scope, String path) {
        if (scope == null || path == null) {
            return null;
        }
        if ("this".equals(path)) {
            return scope;
        }
        JsonNode current = scope;
        for (String part : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            if (current.isArray()) {
                try {
                    current = current.get(Integer.parseInt(part));
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                current = current.get(part);
            }
        }
        return current;
    }

    private boolean isTruthy(JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return node.asDouble() != 0;
        }
        if (node.isTextual()) {
            return !node.asText().isBlank();
        }
        if (node.isArray()) {
            return node.size() > 0;
        }
        return true;
    }
}
