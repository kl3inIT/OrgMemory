package com.orgmemory.core.knowledge;

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
