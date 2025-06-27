package com.example.agenttest.dto;

public class CDCResponse {
    private String document;
    private String message;

    // Constructeur
    public CDCResponse(String document, String message) {
        this.document = document;
        this.message = message;
    }

    // Getters
    public String getDocument() { return document; }
    public String getMessage() { return message; }
}