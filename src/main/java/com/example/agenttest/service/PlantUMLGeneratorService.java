package com.example.agenttest.service;

import com.example.agenttest.dto.gemini.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.stream.Collectors;

@Service
public class PlantUMLGeneratorService {
    private static final Logger logger = LoggerFactory.getLogger(PlantUMLGeneratorService.class);

    public String generatePlantUML(GeminiResponse diagramData) {
        if (diagramData == null || (CollectionUtils.isEmpty(diagramData.getClasses()) && CollectionUtils.isEmpty(diagramData.getRelationships()))) {
            logger.warn("Diagram data is empty. Returning empty PlantUML string.");
            return "@startuml\n' No data to display\n@enduml";
        }

        StringBuilder plantUml = new StringBuilder();
        plantUml.append("@startuml\n\n");
        plantUml.append("'skinparam style strictuml\n"); // Optional: for a stricter UML look
        plantUml.append("skinparam classAttributeIconSize 0\n"); // Hide attribute icons
        plantUml.append("hide empty members\n\n"); // Hide empty attributes/methods sections


        // Define classes
        if (diagramData.getClasses() != null) {
            for (ClassDetail classDetail : diagramData.getClasses()) {
                if (classDetail == null || !StringUtils.hasText(classDetail.getName())) continue;

                String classType = "class";
                if (StringUtils.hasText(classDetail.getStereotype())) {
                    if ("interface".equalsIgnoreCase(classDetail.getStereotype())) {
                        classType = "interface";
                    } else if ("abstract".equalsIgnoreCase(classDetail.getStereotype())) {
                        classType = "abstract class";
                    }
                    // You could add <<stereotype>> if it's not a keyword
                }

                plantUml.append(String.format("%s \"%s\" {\n", classType, classDetail.getName()));

                // Attributes
                if (!CollectionUtils.isEmpty(classDetail.getAttributes())) {
                    for (AttributeDetail attr : classDetail.getAttributes()) {
                        if (attr == null || !StringUtils.hasText(attr.getName())) continue;
                        plantUml.append(String.format("  %s%s : %s\n",
                                getVisibilityChar(attr.getVisibility()),
                                attr.getName(),
                                StringUtils.hasText(attr.getType()) ? attr.getType() : " ")); // Use space if type is missing for cleaner look
                    }
                }

                // Methods
                if (!CollectionUtils.isEmpty(classDetail.getMethods())) {
                    for (MethodDetail method : classDetail.getMethods()) {
                        if (method == null || !StringUtils.hasText(method.getName())) continue;
                        String params = "";
                        if (!CollectionUtils.isEmpty(method.getParameters())) {
                            params = method.getParameters().stream()
                                    .filter(p -> p != null && StringUtils.hasText(p.getName()))
                                    .map(p -> String.format("%s%s",
                                            p.getName(),
                                            StringUtils.hasText(p.getType()) ? ": " + p.getType() : ""))
                                    .collect(Collectors.joining(", "));
                        }
                        plantUml.append(String.format("  %s%s(%s)%s\n",
                                getVisibilityChar(method.getVisibility()),
                                method.getName(),
                                params,
                                StringUtils.hasText(method.getReturnType()) ? " : " + method.getReturnType() : ""));
                    }
                }
                plantUml.append("}\n\n");
            }
        }

        // Define relationships
        if (diagramData.getRelationships() != null) {
            for (RelationshipDetail rel : diagramData.getRelationships()) {
                if (rel == null || !StringUtils.hasText(rel.getType())) continue;

                String source = rel.getSource();
                String target = rel.getTarget();
                String label = StringUtils.hasText(rel.getLabel()) ? " : " + rel.getLabel() : "";
                String multSource = StringUtils.hasText(rel.getMultiplicitySource()) ? String.format("\"%s\" ", rel.getMultiplicitySource()) : "";
                String multTarget = StringUtils.hasText(rel.getMultiplicityTarget()) ? String.format(" \"%s\"", rel.getMultiplicityTarget()) : "";


                // For aggregation/composition, prefer container/part if available
                // And use multiplicityTarget for the 'part'/'target' end
                if (("Aggregation".equalsIgnoreCase(rel.getType()) || "Composition".equalsIgnoreCase(rel.getType()))
                        && StringUtils.hasText(rel.getContainer()) && StringUtils.hasText(rel.getPart())) {
                    source = rel.getContainer();
                    target = rel.getPart();
                    // Corrected: Use multiplicityTarget as it maps to the 'part'/'target' end
                    if (StringUtils.hasText(rel.getMultiplicityTarget())) {
                        multTarget = String.format(" \"%s\"", rel.getMultiplicityTarget());
                    }
                    // Note: multiplicitySource for 'container' is handled by the initial assignment
                }


                if (!StringUtils.hasText(source) || !StringUtils.hasText(target)) {
                    logger.warn("Skipping relationship due to missing source or target: {}", rel);
                    continue;
                }

                String relationshipArrow = "";
                // Handle Aggregation and Composition separately as their PlantUML syntax often differs slightly
                if ("Aggregation".equalsIgnoreCase(rel.getType())) {
                    relationshipArrow = "o--"; // Aggregation
                    // PlantUML: ClassA "1" o-- "0..*" ClassB
                    plantUml.append(String.format("\"%s\" %s %s \"%s\"%s%s\n",
                            source, multSource.trim(), relationshipArrow, target, multTarget.trim(), label));
                    continue; // Skip the default formatting below

                } else if ("Composition".equalsIgnoreCase(rel.getType())) {
                    relationshipArrow = "*--"; // Composition
                    // PlantUML: ClassA "1" *-- "1..*" ClassB
                    plantUml.append(String.format("\"%s\" %s %s \"%s\"%s%s\n",
                            source, multSource.trim(), relationshipArrow, target, multTarget.trim(), label));
                    continue; // Skip the default formatting below

                } else {
                    // Handle other relationship types
                    switch (rel.getType().toLowerCase()) {
                        case "inheritance":
                            relationshipArrow = "--|>"; // Generalization
                            break;
                        case "realization":
                            relationshipArrow = "..|>"; // Realization (implements)
                            break;
                        case "association":
                            relationshipArrow = "-->";
                            break;
                        default:
                            logger.warn("Unknown relationship type: {}. Defaulting to association.", rel.getType());
                            relationshipArrow = "-->"; // Default to association
                            break;
                    }
                    // Default relationship formatting (covers Inheritance, Realization, basic Association)
                    plantUml.append(String.format("\"%s\" %s %s \"%s\"%s%s\n",
                            source, multSource.trim(), relationshipArrow, target, multTarget.trim(), label));
                }
            }
        }

        plantUml.append("\n@enduml");
        logger.info("Generated PlantUML code successfully.");
        logger.debug("PlantUML Code:\n{}", plantUml.toString());
        return plantUml.toString();
    }

    private String getVisibilityChar(String visibility) {
        if (!StringUtils.hasText(visibility)) return "+"; // Default to public
        switch (visibility.toLowerCase()) {
            case "private": return "-";
            case "public": return "+";
            case "protected": return "#";
            case "package": return "~";
            default: return "+"; // Default if unknown
        }
    }
}