package com.buddyai.agent.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

/**
 * Central Spring configuration for the agent core module.
 *
 * <p>Provides:
 * <ul>
 *   <li>A {@link ChatClient} wired to {@link AnthropicChatModel} with
 *       sensible defaults (model, max tokens, temperature).</li>
 *   <li>A task {@link Executor} bean for {@code @Async} agent work.</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableAsync
public class AgentConfig {

    /** Claude model to use.  Can be overridden via {@code agent.model} in application.yml. */
    @Value("${agent.model:claude-sonnet-4-6}")
    private String model;

    @Value("${agent.max-tokens:4096}")
    private int maxTokens;

    @Value("${agent.temperature:0.7}")
    private double temperature;

    @Value("${agent.async.core-pool-size:10}")
    private int corePoolSize;

    @Value("${agent.async.max-pool-size:50}")
    private int maxPoolSize;

    @Value("${agent.async.queue-capacity:200}")
    private int queueCapacity;

    // -------------------------------------------------------------------------
    // ChatClient
    // -------------------------------------------------------------------------

    /**
     * Build the primary {@link ChatClient} backed by Claude.
     *
     * <p>The model and generation parameters are applied as defaults so that
     * every call-site can override them per-request when needed.</p>
     *
     * @param chatModel the auto-configured {@link AnthropicChatModel} bean
     * @return a fully configured {@link ChatClient}
     */
    @Bean
    public ChatClient chatClient(AnthropicChatModel chatModel) {
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model(model)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .build();

        return ChatClient.builder(chatModel)
                .defaultOptions(options)
                .build();
    }

    // -------------------------------------------------------------------------
    // RestTemplate
    // -------------------------------------------------------------------------

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // -------------------------------------------------------------------------
    // Async executor
    // -------------------------------------------------------------------------

    /**
     * Task executor for {@code @Async} methods in the agent layer.
     * Exposed under the bean name {@code "agentTaskExecutor"} so that
     * {@code @Async("agentTaskExecutor")} can target it explicitly.
     *
     * @return a thread-pool executor sized for I/O-bound LLM calls
     */
    @Bean(name = "agentTaskExecutor")
    public Executor agentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("agent-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
