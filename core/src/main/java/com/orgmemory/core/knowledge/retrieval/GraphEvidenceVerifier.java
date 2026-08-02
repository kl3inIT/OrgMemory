package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.graphrag.model.EvidenceReference;
import java.util.UUID;

/** Retrieval-owned canonical authorization boundary for Graph evidence reads. */
public interface GraphEvidenceVerifier {

    VerifiedGraphEvidenceScope verifyScope(
            CurrentActor actor,
            String expectedAuthorizationModelId);

    boolean isCurrentGoverningEvidence(
            VerifiedGraphEvidenceScope scope,
            UUID knowledgeSpaceId,
            EvidenceReference evidence);
}
