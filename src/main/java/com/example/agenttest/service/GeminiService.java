package com.example.agenttest.service;

import com.example.agenttest.exception.GeminiApiException;
import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictRequest;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    @Value("${gemini.api.project-id}")
    private String projectId;

    @Value("${gemini.api.location}")
    private String location; // e.g., "us-central1"

    @Value("${gemini.api.model-name}")
    private String modelName; // e.g., "gemini-1.0-pro" or "gemini-1.5-flash-001"


    public String getDiagramDataFromText(String textExplanation) {
        logger.debug("Sending request to Gemini API. Project: {}, Location: {}, Model: {}", projectId, location, modelName);

        String endpoint = String.format("%s-aiplatform.googleapis.com:443", location);
        EndpointName endpointName = EndpointName.ofProjectLocationPublisherModel(projectId, location, "google", modelName);

        try (PredictionServiceClient predictionServiceClient = PredictionServiceClient.create(
                PredictionServiceSettings.newBuilder().setEndpoint(endpoint).build())) {

            String prompt = buildPrompt(textExplanation);
            logger.debug("Constructed Prompt: {}", prompt);

            Value.Builder promptValueBuilder = Value.newBuilder();
            try {
                JsonFormat.parser().merge(String.format("{\"prompt\": \"%s\"}", escapeStringForJson(prompt)), promptValueBuilder);
            } catch (InvalidProtocolBufferException e) {
                logger.error("Error merging prompt into Value: {}", e.getMessage(), e);
                throw new GeminiApiException("Error creating Gemini request.", e);
            }
            Value promptValue = promptValueBuilder.build();

            PredictRequest request = PredictRequest.newBuilder()
                    .setEndpoint(endpointName.toString())
                    .addInputs(promptValue) // 'inputs' is the correct field for structured input
                    .build();

            logger.debug("Sending PredictRequest to Gemini: {}", request.toString().substring(0, Math.min(request.toString().length(), 500))); // Log truncated request

            PredictResponse predictResponse = predictionServiceClient.predict(request);
            logger.debug("Received raw response from Gemini.");

            if (predictResponse.getOutputsCount() > 0) {
                Value outputValue = predictResponse.getOutputs(0);
                String jsonOutput = "";

                if (outputValue.hasStructValue() && outputValue.getStructValue().getFieldsMap().containsKey("content")) {
                    // This handles the structure where the JSON is directly under the "content" key
                    jsonOutput = outputValue.getStructValue().getFieldsOrThrow("content").getStringValue();
                } else if (outputValue.hasStructValue() && outputValue.getStructValue().getFieldsMap().containsKey("candidates")) {
                    // Handle the 'candidates' structure (common in newer Gemini versions)
                    var candidatesList = outputValue.getStructValue().getFieldsOrThrow("candidates").getListValue();
                    if (candidatesList.getValuesCount() > 0) {
                        var firstCandidate = candidatesList.getValues(0).getStructValue();
                        if (firstCandidate.getFieldsMap().containsKey("content")) {
                            var content = firstCandidate.getFieldsOrThrow("content").getStructValue();
                            if (content.getFieldsMap().containsKey("parts") && content.getFieldsOrThrow("parts").getListValue().getValuesCount() > 0) {
                                var firstPart = content.getFieldsOrThrow("parts").getListValue().getValues(0).getStructValue();
                                if (firstPart.getFieldsMap().containsKey("text")) {
                                    jsonOutput = firstPart.getFieldsOrThrow("text").getStringValue();
                                } else {
                                    logger.warn("Gemini response 'parts' array's first element has no 'text' field.");
                                    throw new GeminiApiException("Unexpected Gemini response structure.");
                                }
                            } else {
                                logger.warn("Gemini response 'content' has no 'parts' or 'parts' is empty.");
                                throw new GeminiApiException("Unexpected Gemini response structure.");
                            }
                        } else {
                            logger.warn("Gemini response 'candidates' first element has no 'content' field.");
                            throw new GeminiApiException("Unexpected Gemini response structure.");
                        }
                    } else {
                        logger.warn("Gemini response 'candidates' array is empty.");
                        throw new GeminiApiException("Gemini API returned no valid candidates.");
                    }
                } else if (outputValue.hasStringValue()) {
                    // Fallback if the output is directly a string
                    jsonOutput = outputValue.getStringValue();
                } else {
                    logger.error("Unexpected Gemini output structure: {}", outputValue);
                    throw new GeminiApiException("Unexpected Gemini output structure.");
                }

                logger.debug("Extracted JSON Output from Gemini: {}", jsonOutput.substring(0, Math.min(jsonOutput.length(), 500)));
                return extractJsonFromMarkdown(jsonOutput);

            } else {
                logger.warn("Gemini API returned no outputs.");
                throw new GeminiApiException("Gemini API returned no outputs.");
            }

        } catch (IOException e) {
            logger.error("IOException during Gemini API call: {}", e.getMessage(), e);
            throw new GeminiApiException("Error communicating with Gemini API: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error during Gemini API call: {}", e.getMessage(), e);
            throw new GeminiApiException("An unexpected error occurred with Gemini API: " + e.getMessage(), e);
        }
    }

    private String extractJsonFromMarkdown(String text) {
        if (text.startsWith("```json")) {
            text = text.substring(7);
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
        } else if (text.startsWith("```")) {
            text = text.substring(3);
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
        }
        return text.trim();
    }

    private String escapeStringForJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String buildPrompt(String textExplanation) {
        return String.format("""
        You are an expert software design assistant. Analyze the following text and extract information to create a UML class diagram.
        Identify all classes, their attributes (with data types if inferable, and visibility like public, private, protected, or package), and their methods (with parameters including names and types, return types, and visibility).
        Also, identify relationships between classes:
        - Inheritance (e.g., "class A extends class B")
        - Realization (e.g., "class A implements interface B")
        - Association (e.g., "class A uses class B", "class A has a reference to class B", with optional multiplicity like 1, *, 0..1, 1..*)
        - Aggregation (e.g., "class A has a collection of class B", where B can exist independently)
        - Composition (e.g., "class A is composed of class B", where B cannot exist without A)

        Provide the output STRICTLY in the following JSON format. Do not include any explanatory text before or after the JSON block.
        If a detail (like visibility, type, or multiplicity) is not clearly specified or inferable, you can omit the field or use a sensible default (e.g., "String" for unknown type, "public" for visibility if not specified, or omit multiplicity).
        For attributes and methods, visibility can be one of: "public", "private", "protected", "package".
        For classes, include a "stereotype" field if it's an "interface" or "abstract" class. Otherwise, omit it or set to null.
        For relationships, `type` can be: "Inheritance", "Realization", "Association", "Aggregation", "Composition".
        `multiplicitySource` and `multiplicityTarget` are optional.

        JSON Format:
        {
          "classes": [
            {
              "name": "ClassName",
              "stereotype": "interface | abstract", // optional
              "attributes": [
                {"visibility": "private", "name": "attributeName", "type": "DataType"}
              ],
              "methods": [
                {
                  "visibility": "public",
                  "name": "methodName",
                  "parameters": [{"name": "paramName", "type": "ParamType"}],
                  "returnType": "ReturnType"
                }
              ]
            }
          ],
          "relationships": [
            {
              "type": "Inheritance",
              "source": "ChildClass",
              "target": "ParentClass"
            },
            {
              "type": "Association",
              "source": "ClassA",
              "target": "ClassB",
              "label": "uses", // optional
              "multiplicitySource": "1", // optional
              "multiplicityTarget": "*" // optional
            },
            { // Example for Aggregation
              "type": "Aggregation",
              "container": "ClassC", // Use 'container' and 'part' for Aggregation/Composition
              "part": "ClassD",
              "multiplicityPart": "*" // Multiplicity of the 'part' relative to container
            },
            { // Example for Composition
              "type": "Composition",
              "container": "ClassE", // Use 'container' and 'part' for Aggregation/Composition
              "part": "ClassF",
              "multiplicityPart": "1..*" // Multiplicity of the 'part' relative to container
            }
          ]
        }

        Input Text:
        ---
        %s
        ---
        """, textExplanation);
    }
}