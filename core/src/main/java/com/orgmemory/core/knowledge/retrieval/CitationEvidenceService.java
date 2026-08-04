package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.organization.CurrentActor;
import java.util.List;
import java.util.UUID;

/** Reauthorizes replay metadata and bounded evidence without exposing storage details. */
public interface CitationEvidenceService {

    List<CitationEvidenceReference> hydrate(
            CurrentActor actor, List<UUID> chunkIds, String requestId);

    CitationEvidenceExcerpt excerpt(
            CurrentActor actor, UUID chunkId, String requestId);
}
