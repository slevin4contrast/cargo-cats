package com.contrast.dataservice.ai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.sun.net.httpserver.HttpServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * Service for AI API integration with mocked responses.
 * Uses WireMock to intercept API calls without making real requests.
 * This allows Contrast Security agents to observe API usage instrumentation.
 */
@Service
public class AiService {
    private static final Logger logger = LoggerFactory.getLogger(AiService.class);

    @Value("${ai.demo.enabled:true}")
    private boolean enabled;

    @Value("${ai.demo.port:8888}")
    private int mockServerPort;

    @Value("${ai.demo.host:localhost}")
    private String mockServerHost;

    @Value("${ai.demo.openai.host:mock-openai}")
    private String openaiHost;

    private HttpServer mockHttpServer;
    private OpenAIClient openAIClient;

    @PostConstruct
    public void initialize() {
        logger.info("Initializing AiService. Mock mode enabled: {}", enabled);

        if (enabled) {
            try {
                startMockServer();
            } catch (IOException e) {
                logger.error("Failed to start mock HTTP server", e);
                return;
            }
            initializeClients();
            logger.info("AI Service initialized with mock server on {}:{}", mockServerHost, mockServerPort);
            logger.info("  - OpenAI accessible at: http://{}:{}", openaiHost, mockServerPort);
        }
    }

    private void startMockServer() throws IOException {
        mockHttpServer = HttpServer.create(new InetSocketAddress("0.0.0.0", mockServerPort), 0);

        String chatCompletionResponse = "{\"id\":\"chatcmpl-123\",\"object\":\"chat.completion\",\"created\":1234567890,"
            + "\"model\":\"gpt-4\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\","
            + "\"content\":\"Response from mock OpenAI\"},\"finish_reason\":\"stop\"}],"
            + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20,\"total_tokens\":30}}";

        mockHttpServer.createContext("/chat/completions", exchange -> {
            byte[] response = chatCompletionResponse.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        mockHttpServer.start();
        logger.info("Mock HTTP server started on port {}", mockServerPort);
    }

    private void initializeClients() {
        String openaiBaseUrl = String.format("http://%s:%d", openaiHost, mockServerPort);

        // Initialize OpenAI client pointing to mock server
        openAIClient = OpenAIOkHttpClient.builder()
            .baseUrl(openaiBaseUrl)
            .apiKey("sk-test-key")
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
                    .model(ChatModel.GPT_4_TURBO)
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
        if (mockHttpServer != null) {
            mockHttpServer.stop(0);
            logger.info("Mock HTTP server stopped");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
