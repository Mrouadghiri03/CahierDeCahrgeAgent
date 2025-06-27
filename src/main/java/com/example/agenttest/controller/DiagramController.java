package com.example.agenttest.controller;

import com.example.agenttest.dto.PlantUMLResponse;
import com.example.agenttest.dto.TextExplanationRequest;
import com.example.agenttest.exception.GeminiApiException;
import com.example.agenttest.service.GeminiService;
// Removed: import com.fasterxml.jackson.databind.JsonNode;
// Removed: import com.fasterxml.jackson.databind.ObjectMapper;

// Correct PlantUML imports
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/diagrams")
public class DiagramController {

    private static final Logger logger = LoggerFactory.getLogger(DiagramController.class);
    private final GeminiService geminiService;
    // Removed: private final ObjectMapper objectMapper; // No longer needed as we assume direct PlantUML

    // Pattern to extract PlantUML code blocks - supports both with and without markdown fences
    private static final Pattern PLANTUML_PATTERN = Pattern.compile(
            "(?:```\\s*plantuml\\s*\\n)?((?:@startuml[\\s\\S]*?@enduml))(?:\\n```)?",
            Pattern.DOTALL);

    @Autowired
    public DiagramController(GeminiService geminiService /* Removed: , ObjectMapper objectMapper */) {
        this.geminiService = geminiService;
        // Removed: this.objectMapper = objectMapper;
    }

    @PostMapping("/generate-from-text")
    public ResponseEntity<PlantUMLResponse> generateDiagramFromText(@RequestBody TextExplanationRequest request) {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            logger.warn("Received empty text explanation request.");
            return ResponseEntity.badRequest().body(new PlantUMLResponse(null, "Text explanation cannot be empty."));
        }

        try {
            logger.info("Received request to generate diagram from text.");
            String geminiOutput = geminiService.generateCahierDeCharge(request.getText());

            if (geminiOutput == null || geminiOutput.isBlank()) {
                logger.error("Gemini service returned empty output.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new PlantUMLResponse(null, "AI service returned empty data."));
            }

            // Extract PlantUML source from Gemini output
            String plantUmlSource = extractPlantUMLSourceFromOutput(geminiOutput);

            if (plantUmlSource == null || plantUmlSource.isBlank()) {
                logger.error("Could not extract PlantUML source from Gemini output. Gemini output: {}", geminiOutput);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new PlantUMLResponse(null, "Failed to parse diagram source from AI response. " +
                                "Expected PlantUML starting with '@startuml' and ending with '@enduml'."));
            }

            // Generate SVG from PlantUML source
            String svgDiagram = generateSvgFromPlantUML(plantUmlSource);

            return ResponseEntity.ok(new PlantUMLResponse(svgDiagram, "Diagram generated successfully."));

        } catch (GeminiApiException e) {
            logger.error("Gemini API error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PlantUMLResponse(null, "Error communicating with AI service: " + e.getMessage()));
        } catch (IOException e) { // Catch IOException specifically for PlantUML generation issues
            logger.error("Error generating diagram: {}", e.getMessage(), e);
            String errorMessage = "Failed to generate diagram.";
            if (e.getMessage() != null && e.getMessage().contains("Cannot run program") && e.getMessage().contains("dot")) {
                errorMessage += " This usually indicates that Graphviz 'dot' executable is not found or accessible. " +
                        "Please ensure Graphviz is installed and 'dot' is in your system's PATH, " +
                        "or configure PlantUML to point to its location (e.g., by setting -Dplantuml.graphviz.dot=/path/to/dot).";
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PlantUMLResponse(null, errorMessage));
        } catch (Exception e) { // General catch for other unexpected errors
            logger.error("An unexpected error occurred: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PlantUMLResponse(null, "An unexpected error occurred: " + e.getMessage()));
        }
    }

    /**
     * Extract PlantUML source code from Gemini output.
     * Assumes Gemini always returns PlantUML code.
     * This method tries to extract a PlantUML block, either fenced with markdown or as raw content.
     *
     * @param geminiOutput Output from Gemini service
     * @return PlantUML source code or null if not found
     */
    private String extractPlantUMLSourceFromOutput(String geminiOutput) {
        // Case 1: Check if it's a PlantUML code block (with or without markdown fencing)
        Matcher matcher = PLANTUML_PATTERN.matcher(geminiOutput);
        if (matcher.find()) {
            return matcher.group(1); // Group 1 is the content inside the fences or the raw @startuml/@enduml block
        }

        // Case 2: Fallback for raw PlantUML without typical markers if the regex didn't catch it.
        // This is less likely if the regex is robust but provides a safety net.
        if (geminiOutput.contains("@startuml") && geminiOutput.contains("@enduml")) {
            int startIndex = geminiOutput.indexOf("@startuml");
            int endIndex = geminiOutput.lastIndexOf("@enduml") + "@enduml".length();
            if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                return geminiOutput.substring(startIndex, endIndex);
            }
        }

        logger.warn("Gemini output does not contain a recognizable PlantUML block: {}", geminiOutput);
        return null;
    }

    // Removed all JSON-related methods:
    // private String extractPlantUMLSource(String geminiJsonOutput) throws IOException { ... }
    // private String findPlantUMLSourceInJson(JsonNode node) { ... }
    // private boolean isPotentialPlantUMLSource(String text) { ... }

    /**
     * Generates SVG from PlantUML source
     *
     * @param plantUmlSource PlantUML source code
     * @return SVG string representation
     * @throws IOException if SVG generation fails
     */
    private String generateSvgFromPlantUML(String plantUmlSource) throws IOException {
        SourceStringReader reader = new SourceStringReader(plantUmlSource);
        final ByteArrayOutputStream os = new ByteArrayOutputStream();

        // Write the first image to "os"
        // We specify FileFormat.SVG for direct SVG output
        reader.outputImage(os, new FileFormatOption(FileFormat.SVG));

        return os.toString();
    }
}