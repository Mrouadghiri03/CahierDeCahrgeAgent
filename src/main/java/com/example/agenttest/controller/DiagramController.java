package com.example.agenttest.controller;

import com.example.agenttest.dto.PlantUMLResponse;
import com.example.agenttest.dto.TextExplanationRequest;
import com.example.agenttest.dto.gemini.GeminiResponse;
import com.example.agenttest.exception.GeminiApiException;
import com.example.agenttest.service.DiagramDataParser;
import com.example.agenttest.service.GeminiService;
import com.example.agenttest.service.PlantUMLGeneratorService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/diagrams")
public class DiagramController {

    private static final Logger logger = LoggerFactory.getLogger(DiagramController.class);

    private final GeminiService geminiService;
    private final DiagramDataParser diagramDataParser;
    private final PlantUMLGeneratorService plantUMLGeneratorService;

    @Autowired
    public DiagramController(GeminiService geminiService,
                             DiagramDataParser diagramDataParser,
                             PlantUMLGeneratorService plantUMLGeneratorService) {
        this.geminiService = geminiService;
        this.diagramDataParser = diagramDataParser;
        this.plantUMLGeneratorService = plantUMLGeneratorService;
    }

    @PostMapping("/generate-from-text")
    public ResponseEntity<PlantUMLResponse> generateDiagramFromText(@RequestBody TextExplanationRequest request) {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            logger.warn("Received empty text explanation request.");
            return ResponseEntity.badRequest().body(new PlantUMLResponse(null, "Text explanation cannot be empty."));
        }

        try {
            logger.info("Received request to generate diagram from text.");
            String geminiJsonOutput = geminiService.getDiagramDataFromText(request.getText());

            if (geminiJsonOutput == null || geminiJsonOutput.isBlank()) {
                logger.error("Gemini service returned empty or null JSON output.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new PlantUMLResponse(null, "AI service returned empty data."));
            }

            GeminiResponse diagramData = diagramDataParser.parseGeminiResponse(geminiJsonOutput);
            String plantUMLCode = plantUMLGeneratorService.generatePlantUML(diagramData);

            return ResponseEntity.ok(new PlantUMLResponse(plantUMLCode, "Diagram generated successfully."));

        } catch (GeminiApiException e) {
            logger.error("Gemini API error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PlantUMLResponse(null, "Error communicating with AI service: " + e.getMessage()));
        } catch (JsonProcessingException e) {
            logger.error("Error parsing AI response: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PlantUMLResponse(null, "Error processing AI response: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("An unexpected error occurred: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PlantUMLResponse(null, "An unexpected error occurred: " + e.getMessage()));
        }
    }
}