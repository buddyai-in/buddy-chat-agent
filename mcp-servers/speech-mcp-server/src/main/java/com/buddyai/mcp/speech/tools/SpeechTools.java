package com.buddyai.mcp.speech.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class SpeechTools {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${OPENAI_API_KEY:}")
    private String openAiKey;

    @Value("${speech.provider:openai}")
    private String defaultProvider;

    @Tool(description = "Transcribe audio to text. Accepts base64-encoded audio data. Supports WAV, MP3, M4A, WEBM formats.")
    public String transcribeAudio(
            @ToolParam(description = "Base64-encoded audio file content") String base64Audio,
            @ToolParam(description = "Audio format: wav, mp3, m4a, webm") String format,
            @ToolParam(description = "Language code hint e.g. 'en', 'hi' (optional, leave empty for auto-detect)") String language
    ) {
        try {
            // Use OpenAI Whisper via multipart form upload
            byte[] audioBytes = Base64.getDecoder().decode(base64Audio);

            // Build multipart request to OpenAI Whisper
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + openAiKey);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            org.springframework.util.LinkedMultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("model", "whisper-1");
            body.add("file", new org.springframework.core.io.ByteArrayResource(audioBytes) {
                @Override
                public String getFilename() {
                    return "audio." + format;
                }
            });
            if (language != null && !language.isBlank()) {
                body.add("language", language);
            }

            HttpEntity<org.springframework.util.LinkedMultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.openai.com/v1/audio/transcriptions",
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            Map<?, ?> responseBody = response.getBody();
            String transcription = responseBody != null ? (String) responseBody.get("text") : "";
            return objectMapper.writeValueAsString(Map.of(
                    "transcription", transcription,
                    "provider", "openai-whisper",
                    "format", format
            ));
        } catch (Exception e) {
            log.error("Transcription failed", e);
            return "{\"error\": \"Transcription failed: " + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "Convert text to speech audio. Returns base64-encoded audio data.")
    public String synthesizeSpeech(
            @ToolParam(description = "Text to convert to speech") String text,
            @ToolParam(description = "Voice to use: alloy, echo, fable, onyx, nova, shimmer (OpenAI voices)") String voice,
            @ToolParam(description = "Output format: mp3, opus, aac, flac") String format
    ) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + openAiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = Map.of(
                    "model", "tts-1",
                    "input", text,
                    "voice", voice != null && !voice.isBlank() ? voice : "alloy",
                    "response_format", format != null && !format.isBlank() ? format : "mp3"
            );

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    "https://api.openai.com/v1/audio/speech",
                    HttpMethod.POST,
                    requestEntity,
                    byte[].class
            );

            byte[] audioBytes = response.getBody();
            String base64Audio = Base64.getEncoder().encodeToString(audioBytes != null ? audioBytes : new byte[0]);

            return objectMapper.writeValueAsString(Map.of(
                    "audioBase64", base64Audio,
                    "format", format != null ? format : "mp3",
                    "voice", voice != null ? voice : "alloy",
                    "provider", "openai-tts"
            ));
        } catch (Exception e) {
            log.error("Speech synthesis failed", e);
            return "{\"error\": \"Speech synthesis failed: " + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "List available voices for text-to-speech synthesis.")
    public String listAvailableVoices() {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "provider", "openai",
                    "voices", new String[]{"alloy", "echo", "fable", "onyx", "nova", "shimmer"},
                    "note", "All voices support multiple languages automatically"
            ));
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "Detect the language of spoken audio. Returns language code and confidence.")
    public String detectAudioLanguage(
            @ToolParam(description = "Base64-encoded audio file content") String base64Audio,
            @ToolParam(description = "Audio format: wav, mp3, m4a, webm") String format
    ) {
        try {
            // Use Whisper with verbose JSON to get language detection
            byte[] audioBytes = Base64.getDecoder().decode(base64Audio);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + openAiKey);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            org.springframework.util.LinkedMultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("model", "whisper-1");
            body.add("response_format", "verbose_json");
            body.add("file", new org.springframework.core.io.ByteArrayResource(audioBytes) {
                @Override
                public String getFilename() {
                    return "audio." + format;
                }
            });

            HttpEntity<org.springframework.util.LinkedMultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.openai.com/v1/audio/transcriptions",
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            Map<?, ?> responseBody = response.getBody();
            String detectedLanguage = responseBody != null ? (String) responseBody.get("language") : "unknown";

            return objectMapper.writeValueAsString(Map.of(
                    "language", detectedLanguage,
                    "transcription", responseBody != null ? responseBody.get("text") : "",
                    "provider", "openai-whisper"
            ));
        } catch (Exception e) {
            log.error("Language detection failed", e);
            return "{\"error\": \"Language detection failed: " + e.getMessage() + "\"}";
        }
    }
}
