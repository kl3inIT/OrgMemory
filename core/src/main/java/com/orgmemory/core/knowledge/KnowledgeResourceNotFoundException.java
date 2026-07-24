package com.orgmemory.core.knowledge;

/** Opaque not-found result for absent or cross-tenant knowledge resources. */
public final class KnowledgeResourceNotFoundException extends RuntimeException {

    public KnowledgeResourceNotFoundException() {
        super("The requested knowledge resource is not available");
    }
}
