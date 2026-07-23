package com.orgmemory.graphrag.port;

import com.orgmemory.graphrag.authorization.AuthorizedGraphScope;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.query.RankedItem;
import java.util.List;
import java.util.UUID;

public interface GraphEmbeddingIndex {

    void replaceRevisionEmbeddings(GraphRevisionEmbeddings embeddings);

    List<RankedItem<CanonicalEntity>> searchEntitiesByVector(
            AuthorizedGraphScope scope,
            UUID embeddingProfileId,
            int embeddingDimensions,
            List<Float> queryEmbedding,
            double maximumCosineDistance,
            int limit);

    List<RankedItem<CanonicalRelation>> searchRelationsByVector(
            AuthorizedGraphScope scope,
            UUID embeddingProfileId,
            int embeddingDimensions,
            List<Float> queryEmbedding,
            double maximumCosineDistance,
            int limit);
}
