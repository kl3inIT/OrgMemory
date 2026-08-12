package com.orgmemory.core.assistant;

public record AssistantFileProcessingProfile(String canonicalForm, String sha256) {
    public AssistantFileProcessingProfile {
        if (canonicalForm == null || canonicalForm.isBlank()) {
            throw new IllegalArgumentException("canonicalForm is required");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be lowercase hexadecimal");
        }
    }
}
