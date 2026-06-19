package com.buddyai.agent.dto;

import lombok.Builder;

import java.util.Map;

/**
 * Inbound request from any channel (REST, Telegram, WhatsApp, Teams, …).
 */
@Builder
public record AgentRequest(
        String message,
        String sessionId,
        String clientId,
        String userId,
        String channel,
        Map<String, Object> metadata
) {

    /** Supported inbound channel identifiers. */
    public enum Channel {
        REST, TELEGRAM, WHATSAPP, TEAMS
    }
}
