package com.example.agenttest.service;

import com.example.agenttest.exception.GeminiApiException;
import com.google.cloud.aiplatform.v1.*;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;

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

            Value.Builder instanceValue = Value.newBuilder();
            JsonFormat.parser().merge(String.format("{\"prompt\": \"%s\"}", escapeStringForJson(prompt)), instanceValue);
            // For newer Gemini models that support direct text prompts in a structured way:
            // You might need to structure the input according to the specific model's requirements.
            // For example, some models expect a `content` block with `parts` and `text`.
            // The `google-cloud-aiplatform` library might have specific builders for this.
            // This example uses a generic JSON approach which is common for models expecting a JSON payload.
            // Refer to the official Google Cloud documentation for the exact input schema for your chosen Gemini model.

            // If your Gemini model (e.g., gemini-1.5-flash) expects a `contents` structure:
            // Content content = Content.newBuilder()
            //    .setRole("user")
            //    .addParts(Part.newBuilder().setText(prompt).build())
            //    .build();
            // PredictRequest request = PredictRequest.newBuilder()
            //    .setEndpoint(endpointName.toString())
            //    .addInstances(Value.newBuilder().setStructValue(Struct.newBuilder()
            //        .putFields("contents", Value.newBuilder().setListValue(ListValue.newBuilder().addValues(Value.newBuilder().setStructValue(content.toBuilder()))).build())
            //        .build()).build())
            //    .build();

            // Simpler prompt for models like gemini-1.0-pro that might take a direct prompt string within instances:
            PredictRequest request = PredictRequest.newBuilder()
                    .setEndpoint(endpointName.toString())
                    .addInstances(instanceValue.build())
                    .build();


            logger.debug("Sending PredictRequest to Gemini: {}", request.toString().substring(0, Math.min(request.toString().length(), 500))); // Log truncated request

            PredictResponse predictResponse = predictionServiceClient.predict(request);
            logger.debug("Received raw response from Gemini.");

            if (predictResponse.getPredictionsCount() > 0) {
                // The actual structure of the response depends heavily on the Gemini model and version.
                // For models returning JSON in a string field (often 'content' or within a structure):
                Value prediction = predictResponse.getPredictions(0);
                String jsonOutput;

                if (prediction.hasStructValue() && prediction.getStructValue().getFieldsMap().containsKey("candidates")) {
                    // Handling for structure like gemini-1.5-flash
                    // { "candidates": [ { "content": { "role": "model", "parts": [ { "text": "JSON_HERE" } ] } } ] }
                    var candidatesList = prediction.getStructValue().getFieldsOrThrow("candidates").getListValue();
                    if (candidatesList.getValuesCount() > 0) {
                        var firstCandidate = candidatesList.getValues(0).getStructValue();
                        var contentStruct = firstCandidate.getFieldsOrThrow("content").getStructValue();
                        var partsList = contentStruct.getFieldsOrThrow("parts").getListValue();
                        if (partsList.getValuesCount() > 0) {
                            jsonOutput = partsList.getValues(0).getStructValue().getFieldsOrThrow("text").getStringValue();
                        } else {
                            throw new GeminiApiException("Gemini response 'parts' array is empty.");
                        }
                    } else {
                        throw new GeminiApiException("Gemini response 'candidates' array is empty.");
                    }
                } else if (prediction.hasStructValue() && prediction.getStructValue().getFieldsMap().containsKey("content")) {
                    // Handling for older/other structures that might have a direct 'content' field with the JSON string
                    jsonOutput = prediction.getStructValue().getFieldsOrThrow("content").getStringValue();
                }
                else if (prediction.hasStringValue()) {
                    // Fallback if the prediction itself is just a string (less common for structured JSON output)
                    jsonOutput = prediction.getStringValue();
                }
                else {
                    // Log the prediction structure if it's not what we expect
                    logger.error("Unexpected Gemini prediction structure: {}", prediction);
                    throw new GeminiApiException("Unexpected Gemini prediction structure. Expected a structure containing 'candidates' or 'content', or a direct string value.");
                }

                logger.debug("Extracted JSON Output from Gemini: {}", jsonOutput.substring(0, Math.min(jsonOutput.length(),500)));
                // Ensure the output is actual JSON and not wrapped in markdown code blocks
                return extractJsonFromMarkdown(jsonOutput);
            } else {
                logger.warn("Gemini API returned no predictions.");
                throw new GeminiApiException("Gemini API returned no predictions.");
            }
        } catch (InvalidProtocolBufferException e) {
            logger.error("Error parsing JSON for Gemini request: {}", e.getMessage(), e);
            throw new GeminiApiException("Error creating request for Gemini: " + e.getMessage(), e);
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
        // This prompt is crucial. You will need to iterate on it.
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