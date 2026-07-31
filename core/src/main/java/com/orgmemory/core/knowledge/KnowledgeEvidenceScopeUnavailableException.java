package com.orgmemory.core.knowledge;

public final class KnowledgeEvidenceScopeUnavailableException extends RuntimeException {

    private final String reasonCode;
    private final String policyVersion;

    public KnowledgeEvidenceScopeUnavailableException(
            String reasonCode,
            String policyVersion) {
        super("Knowledge evidence authorization scope is unavailable");
        this.reasonCode = required(reasonCode, "reasonCode");
        this.policyVersion = policyVersion == null ? "" : policyVersion.strip();
    }

    String reasonCode() {
        return reasonCode;
    }

    String policyVersion() {
        return policyVersion;
    }

    private static String required(String value, String field) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }
}
