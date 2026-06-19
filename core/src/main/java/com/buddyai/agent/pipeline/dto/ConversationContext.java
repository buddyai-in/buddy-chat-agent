package com.buddyai.agent.pipeline.dto;

import com.buddyai.agent.entity.IntegratedService;
import com.buddyai.agent.entity.Slot;
import com.buddyai.agent.enums.StateSubType;
import com.buddyai.agent.enums.StateType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationContext {

    private String requestId;
    private String sessionId;
    private String clientId;
    private String userId;
    private String input;
    private StateType state;
    private StateSubType stateSubType;
    private String intentSlug;
    private Long serviceId;
    private List<Slot> slots;
    private IntegratedService service;
    private String intentResponse;
    private String summaryPrompt;
    private String responseText;

    /** Presentation type of the reply: TEXT (default) or DOWNLOADABLE. */
    private String responseType;

    /** Download URL when the agent produced a file/CSV/media artifact. */
    private String downloadLink;

    private Map<String, Object> metadata;
}
