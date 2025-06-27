package com.example.agenttest.controller;

import com.example.agenttest.dto.CDCRequest;
import com.example.agenttest.dto.CDCResponse;
import com.example.agenttest.exception.GenerationException;
import com.example.agenttest.service.CahierDeChargeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cdc")
public class CahierDeChargeController {

    private static final Logger logger = LoggerFactory.getLogger(CahierDeChargeController.class);
    private final CahierDeChargeService cdcService;

    @Autowired
    public CahierDeChargeController(CahierDeChargeService cdcService) {
        this.cdcService = cdcService;
    }

    @PostMapping("/generate")
    public ResponseEntity<CDCResponse> generateCDC(@RequestBody CDCRequest request) {
        try {
            String generatedCDC = cdcService.generateCahierDeCharge(request.getDescription());

            // Validation supplémentaire
            if(generatedCDC == null || generatedCDC.isBlank()) {
                throw new GenerationException("Le document généré est vide");
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CDCResponse(generatedCDC, "CDC généré avec succès"));

        } catch (GenerationException e) {
            logger.error("Erreur de génération: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(new CDCResponse(null, "Erreur: " + e.getMessage()));
        }
    }
    // Endpoint supplémentaire pour les templates
    @GetMapping("/template")
    public ResponseEntity<CDCResponse> getCDCTemplate() {
        String template = """
            # Template de CDC
            ## 1. Introduction
            - **Objectif** : [Décrire le but du projet]
            - **Portée** : [Ce qui est inclus/exclu]
            
            ## 2. Exigences Fonctionnelles
            - [Liste des fonctionnalités]
            
            ## 3. Exigences Techniques
            - [Technologies, contraintes]""";

        return ResponseEntity.ok(new CDCResponse(template, "Template de base"));
    }
}