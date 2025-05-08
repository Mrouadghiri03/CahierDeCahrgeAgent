package com.example.agenttest.dto.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RelationshipDetail {
    private String type; // e.g., "Inheritance", "Association", "Aggregation", "Composition", "Realization"
    private String source;
    private String target;
    private String label;
    private String multiplicitySource;
    private String multiplicityTarget;

    // For Aggregation/Composition, clearer field names might be:
    private String container; // (for Aggregation/Composition source)
    private String part;      // (for Aggregation/Composition target)


}