package com.orgmemory.core.knowledge;

final class CanonicalEvidenceAuthorizationException extends RuntimeException {

    private final String reasonCode;
    private final String authorizationModelId;

    CanonicalEvidenceAuthorizationException(
            String reasonCode,
            String authorizationModelId) {
        super("Canonical evidence is not visible");
        this.reasonCode = reasonCode;
        this.authorizationModelId = authorizationModelId;
    }

    String reasonCode() {
        return reasonCode;
    }

    String authorizationModelId() {
        return authorizationModelId;
    }
}
