package com.contrast.dataservice.ai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Service for AI API integration using a local Ollama model.
 * The OpenAI Java SDK is pointed at the Ollama OpenAI-compatible endpoint,
 * allowing Contrast Security agents to observe real AI API usage instrumentation.
 */
@Service
public class AiService {
    private static final Logger logger = LoggerFactory.getLogger(AiService.class);

    @Value("${ai.demo.enabled:true}")
    private boolean enabled;

    @Value("${ai.demo.openai.base-url:http://ollama:11434/v1}")
    private String openaiBaseUrl;

    @Value("${ai.demo.model:smollm:135m}")
    private String modelName;

    private OpenAIClient openAIClient;

    @PostConstruct
    public void initialize() {
        logger.info("Initializing AiService. Enabled: {}", enabled);
        if (enabled) {
            initializeClients();
            logger.info("AI Service initialized. OpenAI base URL: {}, model: {}", openaiBaseUrl, modelName);
        }
    }

    private void initializeClients() {
        openAIClient = OpenAIOkHttpClient.builder()
            .baseUrl(openaiBaseUrl)
            .apiKey("ollama")
            .build();
        logger.info("OpenAI client initialized with base URL: {}", openaiBaseUrl);
    }

    /**
     * Calls OpenAI API to generate a chat completion.
     * Contrast agent will detect and instrument this API usage.
     */
    public String openai(String prompt) {
        if (!enabled || openAIClient == null) {
            return "Service not initialized";
        }

        try {
            logger.info("Calling OpenAI API with prompt: {}", prompt);
            
            var response = openAIClient.chat().completions().create(
                ChatCompletionCreateParams.builder()
                    .model(ChatModel.of(modelName))
                    .addUserMessage(prompt != null ? prompt : "Hello")
                    .build()
            );

            String content = response.choices().get(0).message().content().orElse("No content");
            logger.info("OpenAI response: {}", content);
            return content;
        } catch (Exception e) {
            logger.error("Error calling OpenAI API", e);
            return "Error: " + e.getMessage();
        }
    }

    @PreDestroy
    public void cleanup() {
        if (openAIClient != null) {
            openAIClient.close();
            logger.info("OpenAI client closed");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
