package com.example.agenttest.dto.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MethodDetail {
    private String visibility; // e.g., "public", "private", "protected"
    private String name;
    private List<ParameterDetail> parameters;
    private String returnType;
}