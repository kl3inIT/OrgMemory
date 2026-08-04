package com.orgmemory.core.assistant;

import java.util.Objects;
import java.util.UUID;

/** Internal replay reference. API adapters must reauthorize before serialization. */
public record AssistantCitationReference(int citationNumber, UUID chunkId) {

    public AssistantCitationReference {
        if (citationNumber < 1 || citationNumber > 100) {
            throw new IllegalArgumentException("citationNumber must be between 1 and 100");
        }
        Objects.requireNonNull(chunkId, "chunkId");
    }
}
