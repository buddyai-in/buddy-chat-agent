package com.buddyai.agent.endpoint;

import com.buddyai.agent.agent.orchestrator.OrchestratorAgent;
import com.buddyai.agent.dto.AgentRequest;
import com.buddyai.agent.dto.AgentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Webhook receiver for Telegram Bot API updates.
 *
 * <p>Register this URL with Telegram via:
 * {@code POST https://api.telegram.org/bot{TOKEN}/setWebhook?url=https://your-host/webhook/telegram}
 *
 * <p>The endpoint always returns HTTP 200 with body {@code "ok"} so that Telegram
 * does not retry the delivery even when processing fails.
 */
@RestController
@RequestMapping("/webhook/telegram")
@Slf4j
@RequiredArgsConstructor
public class TelegramWebhookEndpoint {

    private final OrchestratorAgent orchestrator;

    @Value("${telegram.default-client-id:default}")
    private String defaultClientId;

    /**
     * Handle an incoming Telegram update.
     *
     * @param update the raw JSON update payload from Telegram
     * @return HTTP 200 with body {@code "ok"}
     */
    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<String> handleWebhook(@RequestBody Map<String, Object> update) {
        try {
            Map<String, Object> message = (Map<String, Object>) update.get("message");
            if (message == null) {
                log.debug("Telegram update contained no message field, ignoring");
                return ResponseEntity.ok("ok");
            }

            Map<String, Object> chat = (Map<String, Object>) message.get("chat");
            if (chat == null) {
                log.debug("Telegram message contained no chat field, ignoring");
                return ResponseEntity.ok("ok");
            }

            String chatId = chat.get("id").toString();
            String text = (String) message.getOrDefault("text", "");
            String userId = chatId;

            if (text.isBlank()) {
                log.debug("Telegram message from chat={} had no text content, ignoring", chatId);
                return ResponseEntity.ok("ok");
            }

            AgentRequest request = AgentRequest.builder()
                    .message(text)
                    .sessionId("telegram-" + chatId)
                    .clientId(defaultClientId)
                    .userId(userId)
                    .channel(AgentRequest.Channel.TELEGRAM.name())
                    .build();

            AgentResponse response = orchestrator.chat(request);
            log.info("Telegram response for chat {}: {}",
                    chatId,
                    response.message().substring(0, Math.min(50, response.message().length())));

        } catch (Exception e) {
            log.error("Telegram webhook processing error", e);
        }

        return ResponseEntity.ok("ok");
    }
}
