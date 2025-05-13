package com.example.agenttest.service;

import com.example.agenttest.exception.GeminiApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.model-name}")
    private String modelName; // e.g., "gemini-2.0-flash"

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String getDiagramDataFromText(String textExplanation) {
        try {
            final String prompt = buildPrompt(textExplanation);
            final String responseText = callGeminiApi(prompt);

            // Return the raw response text - let the parser handle the specific format
            return responseText;
        } catch (Exception e) {
            logger.error("Error calling Gemini API", e);
            throw new GeminiApiException("Failed to generate diagram data: " + e.getMessage(), e);
        }
    }

    private String callGeminiApi(String prompt) {
        // Prepare headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Prepare request body
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> part = new HashMap<>();

        part.put("text", prompt);
        content.put("parts", List.of(part));
        requestBody.put("contents", List.of(content));

        // Create request entity
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        // Make API call
        String url = String.format(GEMINI_API_URL, modelName, apiKey);
        ResponseEntity<String> responseEntity = restTemplate.postForEntity(url, request, String.class);

        if (!responseEntity.getStatusCode().is2xxSuccessful()) {
            throw new GeminiApiException("Gemini API returned non-success status: " + responseEntity.getStatusCode());
        }

        String responseBody = responseEntity.getBody();
        logger.debug("Gemini API raw response: {}", responseBody);

        try {
            // Parse the JSON response manually to extract the text content
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

            if (responseMap.containsKey("candidates")) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseMap.get("candidates");
                if (!candidates.isEmpty()) {
                    Map<String, Object> candidate = candidates.get(0);
                    if (candidate.containsKey("content")) {
                        Map<String, Object> content1 = (Map<String, Object>) candidate.get("content");
                        if (content1.containsKey("parts")) {
                            List<Map<String, Object>> parts = (List<Map<String, Object>>) content1.get("parts");
                            if (!parts.isEmpty() && parts.get(0).containsKey("text")) {
                                return (String) parts.get(0).get("text");
                            }
                        }
                    }
                }
            }

            logger.error("Unable to parse expected content from Gemini response: {}", responseBody);
            throw new GeminiApiException("Failed to parse Gemini API response");
        } catch (Exception e) {
            logger.error("Error parsing Gemini API response: {}", e.getMessage());
            throw new GeminiApiException("Failed to parse Gemini API response: " + e.getMessage(), e);
        }
    }

    private String buildPrompt(String textExplanation) {
        // Modify the prompt to specifically request diagram data in JSON format


        return """
Analyze the following text and identify the key concepts (e.g., entities, objects, roles) and their relationships (e.g., associations, dependencies, inheritance, composition).

Then, generate a PlantUML class diagram enclosed within @startuml and @enduml tags. Use appropriate PlantUML syntax to represent:

* Classes (with attributes and methods if applicable)
* Associations (with roles and multiplicities if available)
* Inheritance or interfaces (extends/implements)
* Aggregation or composition (if described)

Ensure the PlantUML code is clean and properly formatted.
Do not include explanations or commentary — only return the PlantUML code block.

Example input:
"A School has many Students. Each Student has a name and an ID. Teachers can teach multiple Students. Both Students and Teachers inherit from a common Person entity."

Expected output:
@startuml
class Person {
+name: String
}

class Student {
+id: String
}
Person <|-- Student

class Teacher {
}
Person <|-- Teacher

class School {
}
School "1" --> "many" Student
Teacher "1" --> "many" Student
@enduml

Now process this text:
""" + textExplanation;

    }
}