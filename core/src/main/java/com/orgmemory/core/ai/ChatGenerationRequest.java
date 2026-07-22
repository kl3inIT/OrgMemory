package com.orgmemory.core.ai;

public record ChatGenerationRequest(String systemInstruction, String userPrompt) {

    public ChatGenerationRequest {
        systemInstruction = required(systemInstruction, "systemInstruction");
        userPrompt = required(userPrompt, "userPrompt");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
