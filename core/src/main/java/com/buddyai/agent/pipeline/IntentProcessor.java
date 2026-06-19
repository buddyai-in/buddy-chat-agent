package com.buddyai.agent.pipeline;

import com.buddyai.agent.entity.ServiceIntent;
import com.buddyai.agent.enums.StateType;
import com.buddyai.agent.pipeline.dto.ConversationContext;
import com.buddyai.agent.repository.ServiceIntentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntentProcessor {

    private final ServiceIntentRepository intentRepository;
    private final ChatClient chatClient;

    private static final Set<String> GREETINGS = Set.of(
        "hi", "hello", "hey", "good morning", "good evening", "good afternoon",
        "namaste", "hola", "howdy", "sup", "what's up", "greetings"
    );

    public void process(ConversationContext ctx) {
        String input = ctx.getInput().trim().toLowerCase();

        // Greeting fast-path
        boolean isGreeting = GREETINGS.stream().anyMatch(g ->
            input.equals(g) || input.startsWith(g + " ") || input.startsWith(g + "!"));
        if (isGreeting) {
            ctx.setState(StateType.EMPTY_STATE);
            ctx.setResponseText("Hello! 👋 I'm BuddyAI. How can I help you today?");
            return;
        }

        // Load predefined intents for this client
        List<ServiceIntent> intents = intentRepository.findByClientId(ctx.getClientId());
        if (intents.isEmpty()) {
            ctx.setState(StateType.INTENT_NOT_FOUND);
            ctx.setResponseText(null);
            return;
        }

        // Build intent classification prompt
        String intentList = intents.stream().map(i -> {
            String examples = i.getQuestions() != null && !i.getQuestions().isEmpty()
                ? "\n  Examples: " + String.join(", ", i.getQuestions())
                : "";
            return "- " + i.getIntentSlug()
                + (i.getIntentType() != null ? " (" + i.getIntentType() + ")" : "")
                + examples;
        }).collect(Collectors.joining("\n"));

        String prompt = """
            You are an intent classifier. Given a user query, identify which intent from the list below best matches.
            Return ONLY the intent slug (exactly as shown) or "unknown" if none match.

            Available intents:
            %s

            User query: "%s"

            Intent slug:""".formatted(intentList, ctx.getInput());

        String extracted = chatClient.prompt()
            .user(prompt)
            .call()
            .content()
            .trim()
            .toLowerCase()
            .replaceAll("[\"'\\s]", "");

        Optional<ServiceIntent> matched = intents.stream()
            .filter(i -> i.getIntentSlug().equalsIgnoreCase(extracted))
            .findFirst();

        if (matched.isPresent()) {
            ServiceIntent intent = matched.get();
            ctx.setState(StateType.INTENT_FOUND);
            ctx.setIntentSlug(intent.getIntentSlug());
            ctx.setServiceId(intent.getServiceId());
            ctx.setIntentResponse(intent.getIntentResponse());
            ctx.setSummaryPrompt(intent.getSummaryPrompt());
            log.info("Intent identified: {} for client {}", intent.getIntentSlug(), ctx.getClientId());
        } else {
            ctx.setState(StateType.INTENT_NOT_FOUND);
            ctx.setResponseText(null);
            log.info("No intent matched for: {}", ctx.getInput());
        }
    }
}
