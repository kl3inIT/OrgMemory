package com.orgmemory.core.knowledge.sourceledger;

/** Raised when one immutable source revision would publish under two processing profiles. */
public final class ProcessingProfileMismatchException extends RuntimeException {

    public ProcessingProfileMismatchException(String message) {
        super(message);
    }
}
