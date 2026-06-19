package com.buddyai.agent.response;

import com.buddyai.agent.entity.Slot;
import com.buddyai.agent.enums.StateSubType;
import com.buddyai.agent.enums.StateType;
import com.buddyai.agent.pipeline.dto.ConversationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Capabilities the {@code ResponseAgent} can invoke while deciding how to
 * present an API response.
 *
 * <p>Deliberately <b>not</b> a Spring singleton: a fresh instance is created
 * per turn bound to the live {@link ConversationContext}, so tools like
 * {@link #requestSlotCorrection} can mutate the state machine directly without
 * any thread-locals or shared state. Collaborators ({@link DownloadService})
 * are passed in by the {@code ResponseAgent}.</p>
 *
 * <p>This is the agentic replacement for the old type/status handler matrix:
 * instead of registering a handler per service type and status code, we expose
 * a few verbs and let the model choose.</p>
 */
@Slf4j
public class ResponseTools {

    private final ConversationContext ctx;
    private final DownloadService downloadService;
    private final ObjectMapper objectMapper;

    public ResponseTools(ConversationContext ctx, DownloadService downloadService, ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.downloadService = downloadService;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "Convert a JSON array of records into a downloadable CSV file. "
            + "Use this when the response is tabular/bulk data better delivered as a file than as chat text. "
            + "Returns a download URL to include in your reply.")
    public String formatAsCsv(
            @ToolParam(description = "A JSON array of flat objects, e.g. [{\"name\":\"A\",\"amount\":10}, ...]")
            String jsonArray) {
        try {
            var rows = objectMapper.readValue(jsonArray, java.util.List.class);
            if (rows.isEmpty()) {
                return "ERROR: no rows to export.";
            }
            StringBuilder csv = new StringBuilder();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> first = (java.util.Map<String, Object>) rows.get(0);
            var headers = new java.util.ArrayList<>(first.keySet());
            csv.append(String.join(",", headers)).append("\n");
            for (Object rowObj : rows) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> row = (java.util.Map<String, Object>) rowObj;
                var cells = new java.util.ArrayList<String>();
                for (String h : headers) {
                    Object v = row.get(h);
                    String cell = v == null ? "" : v.toString();
                    if (cell.contains(",") || cell.contains("\"") || cell.contains("\n")) {
                        cell = "\"" + cell.replace("\"", "\"\"") + "\"";
                    }
                    cells.add(cell);
                }
                csv.append(String.join(",", cells)).append("\n");
            }
            String url = downloadService.storeText(csv.toString(), "csv");
            ctx.setResponseType("DOWNLOADABLE");
            ctx.setDownloadLink(url);
            log.info("formatAsCsv produced {} rows at {}", rows.size(), url);
            return "CSV ready at: " + url;
        } catch (Exception e) {
            log.error("formatAsCsv failed: {}", e.getMessage(), e);
            return "ERROR: could not build CSV — " + e.getMessage();
        }
    }

    @Tool(description = "Persist base64-encoded binary content (a file, image, audio, etc.) and return a "
            + "download URL. Use for file/media responses returned by the API.")
    public String generateDownloadLink(
            @ToolParam(description = "Base64-encoded file content") String base64Content,
            @ToolParam(description = "File extension without the dot, e.g. 'pdf', 'png', 'mp3'") String extension) {
        try {
            String url = downloadService.storeBase64(base64Content, extension);
            ctx.setResponseType("DOWNLOADABLE");
            ctx.setDownloadLink(url);
            return "File ready at: " + url;
        } catch (Exception e) {
            log.error("generateDownloadLink failed: {}", e.getMessage(), e);
            return "ERROR: could not store file — " + e.getMessage();
        }
    }

    @Tool(description = "Send the conversation back to slot-collection so the user can correct one input. "
            + "Use this when the API rejected the request because a specific value was invalid or missing. "
            + "After calling this, write a short message telling the user what to fix.")
    public String requestSlotCorrection(
            @ToolParam(description = "The exact slot/parameter name that needs correcting") String slotName,
            @ToolParam(description = "Brief, user-facing reason the value was rejected") String reason) {
        ctx.setState(StateType.SLOTS_INQUIRY);
        ctx.setStateSubType(StateSubType.IN_PROGRESS);
        if (ctx.getSlots() != null) {
            for (Slot s : ctx.getSlots()) {
                if (s.getName() != null && s.getName().equalsIgnoreCase(slotName)) {
                    s.setSlotValue(null);       // clear so the loop asks again
                    s.setIsCurrentSlot(true);
                } else {
                    s.setIsCurrentSlot(false);
                }
            }
        }
        log.info("requestSlotCorrection: slot '{}' cleared for correction ({})", slotName, reason);
        return "OK — '" + slotName + "' will be requested again.";
    }
}
