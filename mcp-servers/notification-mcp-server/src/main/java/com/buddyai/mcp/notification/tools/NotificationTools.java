package com.buddyai.mcp.notification.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.mail.internet.MimeMessage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class NotificationTools {

    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${notification.telegram.bot-token:}")
    private String telegramBotToken;

    @Value("${notification.twilio.account-sid:}")
    private String twilioAccountSid;

    @Value("${notification.twilio.auth-token:}")
    private String twilioAuthToken;

    @Value("${notification.twilio.whatsapp-from:}")
    private String twilioWhatsAppFrom;

    @Value("${notification.meta.whatsapp.access-token:}")
    private String metaWhatsAppAccessToken;

    @Value("${notification.meta.whatsapp.phone-number-id:}")
    private String metaPhoneNumberId;

    @Value("${notification.email.from:noreply@buddyai.com}")
    private String emailFrom;

    public NotificationTools(JavaMailSender mailSender,
                              RestTemplate restTemplate,
                              ObjectMapper objectMapper) {
        this.mailSender = mailSender;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "Send a message to a user via Telegram.")
    public String sendTelegramMessage(
            @ToolParam(description = "Telegram chat ID") String chatId,
            @ToolParam(description = "Message text to send") String message
    ) {
        try {
            if (telegramBotToken == null || telegramBotToken.isBlank()) {
                return errorJson("Telegram bot token is not configured");
            }

            String url = "https://api.telegram.org/bot" + telegramBotToken + "/sendMessage";

            Map<String, String> body = Map.of(
                    "chat_id", chatId,
                    "text", message,
                    "parse_mode", "HTML"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", response.getStatusCode().is2xxSuccessful() ? "sent" : "failed");
            result.put("channel", "telegram");
            result.put("chatId", chatId);
            result.put("httpStatus", response.getStatusCode().value());
            try {
                result.set("response", objectMapper.readTree(response.getBody()));
            } catch (Exception e) {
                result.put("response", response.getBody());
            }
            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            return errorJson("Error sending Telegram message: " + e.getMessage());
        }
    }

    @Tool(description = "Send a WhatsApp message to a phone number.")
    public String sendWhatsAppMessage(
            @ToolParam(description = "Phone number in E.164 format (+1234567890)") String phoneNumber,
            @ToolParam(description = "Message text") String message,
            @ToolParam(description = "Provider: TWILIO or META") String provider
    ) {
        try {
            if ("TWILIO".equalsIgnoreCase(provider)) {
                return sendWhatsAppViaTwilio(phoneNumber, message);
            } else if ("META".equalsIgnoreCase(provider)) {
                return sendWhatsAppViaMeta(phoneNumber, message);
            } else {
                return errorJson("Unknown WhatsApp provider: " + provider + ". Use TWILIO or META");
            }
        } catch (Exception e) {
            return errorJson("Error sending WhatsApp message: " + e.getMessage());
        }
    }

    @Tool(description = "Send an email notification.")
    public String sendEmail(
            @ToolParam(description = "Recipient email address") String to,
            @ToolParam(description = "Email subject") String subject,
            @ToolParam(description = "Email body (plain text or HTML)") String body,
            @ToolParam(description = "Content type: TEXT or HTML") String contentType
    ) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(emailFrom);
            helper.setTo(to);
            helper.setSubject(subject);

            boolean isHtml = "HTML".equalsIgnoreCase(contentType);
            helper.setText(body, isHtml);

            mailSender.send(mimeMessage);

            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", "sent");
            result.put("channel", "email");
            result.put("to", to);
            result.put("subject", subject);
            result.put("contentType", isHtml ? "HTML" : "TEXT");
            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            return errorJson("Error sending email to '" + to + "': " + e.getMessage());
        }
    }

    private String sendWhatsAppViaTwilio(String phoneNumber, String message) throws Exception {
        if (twilioAccountSid == null || twilioAccountSid.isBlank()
                || twilioAuthToken == null || twilioAuthToken.isBlank()) {
            return errorJson("Twilio credentials are not configured");
        }

        String url = "https://api.twilio.com/2010-04-01/Accounts/" + twilioAccountSid + "/Messages.json";

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("From", "whatsapp:" + twilioWhatsAppFrom);
        body.add("To", "whatsapp:" + phoneNumber);
        body.add("Body", message);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(twilioAccountSid, twilioAuthToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("status", response.getStatusCode().is2xxSuccessful() ? "sent" : "failed");
        result.put("channel", "whatsapp");
        result.put("provider", "TWILIO");
        result.put("to", phoneNumber);
        result.put("httpStatus", response.getStatusCode().value());
        try {
            result.set("response", objectMapper.readTree(response.getBody()));
        } catch (Exception e) {
            result.put("response", response.getBody());
        }
        return objectMapper.writeValueAsString(result);
    }

    private String sendWhatsAppViaMeta(String phoneNumber, String message) throws Exception {
        if (metaWhatsAppAccessToken == null || metaWhatsAppAccessToken.isBlank()
                || metaPhoneNumberId == null || metaPhoneNumberId.isBlank()) {
            return errorJson("Meta WhatsApp credentials are not configured");
        }

        String url = "https://graph.facebook.com/v18.0/" + metaPhoneNumberId + "/messages";

        // Normalize phone: remove '+' for Meta API
        String normalizedPhone = phoneNumber.startsWith("+") ? phoneNumber.substring(1) : phoneNumber;

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", normalizedPhone,
                "type", "text",
                "text", Map.of("preview_url", false, "body", message)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(metaWhatsAppAccessToken);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("status", response.getStatusCode().is2xxSuccessful() ? "sent" : "failed");
        result.put("channel", "whatsapp");
        result.put("provider", "META");
        result.put("to", phoneNumber);
        result.put("httpStatus", response.getStatusCode().value());
        try {
            result.set("response", objectMapper.readTree(response.getBody()));
        } catch (Exception e) {
            result.put("response", response.getBody());
        }
        return objectMapper.writeValueAsString(result);
    }

    private String errorJson(String message) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("error", true);
            node.put("message", message);
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            return "{\"error\":true,\"message\":\"" + message.replace("\"", "'") + "\"}";
        }
    }
}
