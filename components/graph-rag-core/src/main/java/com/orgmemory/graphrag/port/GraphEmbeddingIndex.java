package com.orgmemory.graphrag.port;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.query.RankedItem;
import java.util.List;
import java.util.UUID;

public interface GraphEmbeddingIndex {

    void replaceRevisionEmbeddings(GraphRevisionEmbeddings embeddings);

    List<RankedItem<CanonicalEntity>> searchEntitiesByVector(
            AuthorizedEvidenceScope scope,
            UUID embeddingProfileId,
            int embeddingDimensions,
            FloatVector queryEmbedding,
            double maximumCosineDistance,
            int limit);

    List<RankedItem<CanonicalRelation>> searchRelationsByVector(
            AuthorizedEvidenceScope scope,
            UUID embeddingProfileId,
            int embeddingDimensions,
            FloatVector queryEmbedding,
            double maximumCosineDistance,
            int limit);
}
