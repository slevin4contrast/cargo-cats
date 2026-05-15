package com.contrast.dataservice.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST controller for AI API integration.
 * These endpoints trigger API calls that Contrast Security agents will instrument and observe.
 */
@RestController
@CrossOrigin(originPatterns = "*")
public class AiController {
    private static final Logger logger = LoggerFactory.getLogger(AiController.class);

    @Autowired
    private AiService aiService;

    /**
     * Health check endpoint to verify AI service is running.
     * @return status of AI service
     */
    @GetMapping("/api/ai/health")
    public ResponseEntity<String> health() {
        if (aiService.isEnabled()) {
            return ResponseEntity.ok("AI Service is operational");
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("AI Service is not available");
        }
    }

    /**
     * Calls OpenAI API for chat completion.
     * Contrast agents will instrument this call.
     *
     * @param prompt Optional custom prompt
     * @return Result of the API call or error message
     */
    @GetMapping("/api/ai/openai")
    public ResponseEntity<String> openai(
            @RequestParam(value = "prompt", required = false) String prompt) {
        try {
            logger.info("OpenAI endpoint called with prompt: {}", prompt);
            String result = aiService.openai(prompt);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error in OpenAI endpoint", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error: " + e.getMessage());
        }
    }
}
