package com.example.agenttest.dto.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AttributeDetail {
    private String visibility; // e.g., "public", "private", "protected"
    private String name;
    private String type;
}