package com.contrast.frontgateservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class AiServiceProxy {

    @Value("${dataservice.url:http://dataservice:8080}")
    private String dataServiceUrl;

    private final RestTemplate restTemplate;

    public AiServiceProxy() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Health check for AI service
     */
    public ResponseEntity<String> healthCheck() {
        try {
            String url = dataServiceUrl + "/api/ai/health";
            return restTemplate.exchange(url, HttpMethod.GET, null, String.class);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    /**
     * Call OpenAI API endpoint
     */
    public ResponseEntity<String> openai(String prompt) {
        try {
            String url = dataServiceUrl + "/api/ai/openai";
            if (prompt != null && !prompt.isEmpty()) {
                url += "?prompt=" + org.springframework.web.util.UriUtils.encodeQueryParam(prompt, "UTF-8");
            }
            return restTemplate.exchange(url, HttpMethod.GET, null, String.class);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    /**
     * Hidden AI: generate a logistics insight for a rendered report.
     * Called internally during report generation — not exposed as an AI feature in the UI.
     */
    public String summarizeReport(String reportContent) {
        try {
            String url = dataServiceUrl + "/api/ai/summarize-report";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            HttpEntity<String> entity = new HttpEntity<>(reportContent, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
