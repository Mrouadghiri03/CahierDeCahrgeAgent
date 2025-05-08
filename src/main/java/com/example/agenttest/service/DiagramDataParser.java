package com.example.agenttest.service;


import com.example.agenttest.dto.gemini.GeminiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DiagramDataParser {

    private static final Logger logger = LoggerFactory.getLogger(DiagramDataParser.class);
    private final ObjectMapper objectMapper;

    public DiagramDataParser() {
        this.objectMapper = new ObjectMapper();
        // Configure objectMapper if needed, e.g., to ignore unknown properties by default
        // objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // This is now handled by @JsonIgnoreProperties in DTOs
    }

    public GeminiResponse parseGeminiResponse(String jsonResponse) throws JsonProcessingException {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            logger.warn("Received empty or null JSON response for parsing.");
            // Return an empty response object or throw an exception based on desired handling
            return new GeminiResponse(); // Or throw new IllegalArgumentException("JSON response is empty");
        }
        try {
            logger.debug("Attempting to parse JSON response: {}", jsonResponse.substring(0, Math.min(jsonResponse.length(), 500)));
            GeminiResponse response = objectMapper.readValue(jsonResponse, GeminiResponse.class);
            logger.info("Successfully parsed Gemini response into DTOs.");
            return response;
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse JSON response from Gemini: {}", e.getMessage());
            logger.error("Problematic JSON: {}", jsonResponse); // Log the problematic JSON
            throw e; // Re-throw to be handled by the controller
        }
    }
}