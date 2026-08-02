package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.organization.CurrentActor;
import java.util.UUID;

/** Opens one citation through canonical authorization without exposing object-storage details. */
public interface CitationContentService {

    CitationContent open(CurrentActor actor, UUID chunkId, String requestId);
}
