package com.example.agenttest.dto;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlantUMLResponse {
    private String plantUmlCode;
    private String message; // For any additional messages or warnings
}