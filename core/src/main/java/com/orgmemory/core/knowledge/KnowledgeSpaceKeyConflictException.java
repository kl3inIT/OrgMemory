package com.orgmemory.core.knowledge;

/** A Knowledge Space name that derives a key another space in the organization already holds. */
public class KnowledgeSpaceKeyConflictException extends RuntimeException {

    public KnowledgeSpaceKeyConflictException(String message) {
        super(message);
    }
}
