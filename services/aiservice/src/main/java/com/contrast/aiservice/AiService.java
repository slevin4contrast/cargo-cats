package com.contrast.aiservice;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class AiService {
    private static final Logger logger = LoggerFactory.getLogger(AiService.class);

    @Value("${ai.demo.enabled:true}")
    private boolean enabled;

    @Value("${ai.demo.openai.base-url:http://ollama:11434/v1}")
    private String openaiBaseUrl;

    @Value("${ai.demo.model:smollm2:135m}")
    private String modelName;

    private OpenAIClient openAIClient;

    @PostConstruct
    public void initialize() {
        logger.info("Initializing AiService. Enabled: {}", enabled);
        if (enabled) {
            openAIClient = OpenAIOkHttpClient.builder()
                .baseUrl(openaiBaseUrl)
                .apiKey("ollama")
                .build();
            logger.info("OpenAI client initialized. Base URL: {}, model: {}", openaiBaseUrl, modelName);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String chat(String prompt) {
        if (!enabled || openAIClient == null) {
            return "Service not initialized";
        }
        logger.info("Calling AI with prompt: {}", prompt);
        var response = openAIClient.chat().completions().create(
            ChatCompletionCreateParams.builder()
                .model(ChatModel.of(modelName))
                .addUserMessage(prompt != null ? prompt : "Hello")
                .build()
        );
        String content = response.choices().get(0).message().content().orElse("No content");
        logger.info("AI response received");
        return content;
    }

    /**
     * Silently generates a brief logistics insight for a shipping report.
     * Called as a hidden enhancement during report generation — not exposed in the UI as an AI feature.
     * Contrast Security agents detect this instrumented AI SDK call even though it is embedded
     * inside a normal business workflow.
     */
    public String summarizeReport(String reportContent) {
        if (!enabled || openAIClient == null) {
            return null;
        }
        logger.info("Generating logistics insights for report (hidden AI usage)");
        var response = openAIClient.chat().completions().create(
            ChatCompletionCreateParams.builder()
                .model(ChatModel.of(modelName))
                .addSystemMessage("You are a logistics analyst. Given a shipping report, provide a single brief operational insight or recommendation about the shipment in one sentence.")
                .addUserMessage(reportContent)
                .build()
        );
        String insight = response.choices().get(0).message().content().orElse(null);
        logger.info("Generated logistics insight");
        return insight;
    }

    @PreDestroy
    public void cleanup() {
        if (openAIClient != null) {
            openAIClient.close();
            logger.info("OpenAI client closed");
        }
    }
}
