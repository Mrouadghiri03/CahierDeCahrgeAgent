package com.example.agenttest.service;

import com.example.agenttest.exception.GenerationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CahierDeChargeService {

    private static final Logger logger = LoggerFactory.getLogger(CahierDeChargeService.class);
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.model-name}")
    private String modelName;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generateCahierDeCharge(String requirements) throws GenerationException {
        try {
            final String prompt = buildCDCPrompt(requirements);
            final String responseText = callGeminiApi(prompt);
            final String rawDocument = extractCDCText(responseText);

            // Nettoyage du markdown avant retour
            return cleanCDCMarkdown(rawDocument);

        } catch (Exception e) {
            logger.error("Erreur lors de la génération du CDC", e);
            throw new GenerationException("Échec de la génération: " + e.getMessage());
        }
    }
    private String buildCDCPrompt(String requirements) {
        return """
    Génère un cahier des charges technique COMPLET en français au format Markdown strictement SANS ```markdown```. 
    Structure obligatoire :

    # Titre du Projet
    
    ## 1. Introduction
    - **Objectif** : [maximum 3 phrases]
    - **Portée** : 
      - Inclus : [liste]
      - Exclus : [liste]
    
    ## 2. Exigences Fonctionnelles
    ### 2.1. [Module principal]
    - [Fonctionnalité 1] : [description concise]
    - [Fonctionnalité 2] : [description concise]
    
    ### 2.2. [Module secondaire]
    - [...]
    
    ## 3. Exigences Techniques
    - **Frontend** : [technologies]
    - **Backend** : [technologies]
    - **Contraintes** : [liste]
    
    ## 4. Livrables
    - [Item 1] : [description]
    - [Item 2] : [description]
    
    ## 5. Planning
    - **Phase 1** (X semaines) : [description]
    - **Phase 2** (Y semaines) : [...]
    
    Texte à analyser :
    """ + requirements + """
    
    Règles strictes :
    - Pas de ```markdown``` dans la réponse
    - Titres en ## et ### uniquement
    - Listes à puces avec - seulement
    - Maximum 5 niveaux de profondeur
    """;
    }
    private String cleanCDCMarkdown(String rawResponse) {
        // Supprime les blocs ```markdown``` s'ils existent
        return rawResponse.replace("```markdown", "")
                .replace("```", "")
                .trim();
    }

    private String callGeminiApi(String prompt) throws GenerationException {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> content = new HashMap<>();
            Map<String, Object> part = new HashMap<>();

            part.put("text", prompt);
            content.put("parts", List.of(part));
            requestBody.put("contents", List.of(content));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String url = String.format(GEMINI_API_URL, modelName, apiKey);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new GenerationException("API returned status: " + response.getStatusCode());
            }

            return response.getBody();

        } catch (Exception e) {
            throw new GenerationException("Erreur API Gemini: " + e.getMessage());
        }
    }

    private String extractCDCText(String jsonResponse) throws GenerationException {
        try {
            Map<String, Object> responseMap = objectMapper.readValue(jsonResponse, Map.class);

            // Navigation dans la réponse JSON de Gemini
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseMap.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new GenerationException("Aucun candidat dans la réponse de l'API");
            }

            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts == null || parts.isEmpty()) {
                throw new GenerationException("Aucune partie dans le contenu");
            }

            String generatedText = (String) parts.get(0).get("text");
            if (generatedText == null || generatedText.isBlank()) {
                throw new GenerationException("Le texte généré est vide");
            }

            return generatedText;

        } catch (Exception e) {
            throw new GenerationException("Erreur d'extraction du texte: " + e.getMessage());
        }
    }
}