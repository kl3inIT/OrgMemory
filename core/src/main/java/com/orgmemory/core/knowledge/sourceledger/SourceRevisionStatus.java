package com.orgmemory.core.knowledge.sourceledger;

public enum SourceRevisionStatus {
    RECEIVED,
    VALIDATING,
    PARSING,
    CHUNKING,
    EMBEDDING,
    PUBLISHING,
    READY,
    QUARANTINED,
    FAILED
}
