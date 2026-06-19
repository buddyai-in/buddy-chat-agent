package com.buddyai.agent.endpoint;

import com.buddyai.agent.agent.orchestrator.OrchestratorAgent;
import com.buddyai.agent.dto.AgentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Webhook receiver for the Meta WhatsApp Cloud API.
 *
 * <p>Two endpoints are exposed:
 * <ul>
 *   <li>{@code GET /webhook/whatsapp} – subscription verification handshake</li>
 *   <li>{@code POST /webhook/whatsapp} – inbound message delivery</li>
 * </ul>
 */
@RestController
@RequestMapping("/webhook/whatsapp")
@Slf4j
@RequiredArgsConstructor
public class WhatsAppWebhookEndpoint {

    private final OrchestratorAgent orchestrator;

    @Value("${whatsapp.verify-token:buddyai}")
    private String verifyToken;

    @Value("${whatsapp.default-client-id:default}")
    private String defaultClientId;

    /**
     * Meta webhook verification handshake.
     * Meta sends a GET request with {@code hub.mode=subscribe} and a challenge string.
     * We must echo the challenge back if the verify token matches.
     *
     * @param mode      always {@code subscribe} for initial registration
     * @param token     the verify token configured in the Meta developer console
     * @param challenge random string that must be returned verbatim on success
     * @return the challenge string (200) or 403 Forbidden on mismatch
     */
    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("WhatsApp webhook verified successfully");
            return ResponseEntity.ok(challenge);
        }
        log.warn("WhatsApp webhook verification failed: mode={} token={}", mode, token);
        return ResponseEntity.status(403).body("Forbidden");
    }

    /**
     * Handle an inbound WhatsApp Cloud API webhook notification.
     * The payload may contain multiple entries and changes; each text message is
     * dispatched to the orchestrator independently.
     *
     * @param payload the raw JSON payload from Meta
     * @return HTTP 200 with body {@code "ok"}
     */
    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<String> handleWebhook(@RequestBody Map<String, Object> payload) {
        try {
            List<Map<String, Object>> entry = (List<Map<String, Object>>) payload.get("entry");
            if (entry == null || entry.isEmpty()) {
                return ResponseEntity.ok("ok");
            }

            for (Map<String, Object> e : entry) {
                List<Map<String, Object>> changes = (List<Map<String, Object>>) e.get("changes");
                if (changes == null) continue;

                for (Map<String, Object> change : changes) {
                    Map<String, Object> value = (Map<String, Object>) change.get("value");
                    if (value == null) continue;

                    List<Map<String, Object>> messages = (List<Map<String, Object>>) value.get("messages");
                    if (messages == null) continue;

                    for (Map<String, Object> msg : messages) {
                        String from = (String) msg.get("from");
                        Map<String, Object> textObj = (Map<String, Object>) msg.get("text");
                        if (textObj == null) continue;

                        String text = (String) textObj.get("body");
                        if (text == null || text.isBlank()) continue;

                        AgentRequest request = AgentRequest.builder()
                                .message(text)
                                .sessionId("whatsapp-" + from)
                                .clientId(defaultClientId)
                                .userId(from)
                                .channel(AgentRequest.Channel.WHATSAPP.name())
                                .build();

                        orchestrator.chat(request);
                        log.info("Processed WhatsApp message from={}", from);
                    }
                }
            }
        } catch (Exception e) {
            log.error("WhatsApp webhook processing error", e);
        }

        return ResponseEntity.ok("ok");
    }
}
