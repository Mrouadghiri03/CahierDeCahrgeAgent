package com.example.agenttest.dto.gemini;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) // Important for flexibility with Gemini's output
public class GeminiResponse {
    @JsonProperty("classes") // Ensure this matches the key in Gemini's JSON output
    private List<ClassDetail> classes;

    @JsonProperty("relationships") // Ensure this matches the key in Gemini's JSON output
    private List<RelationshipDetail> relationships;
}