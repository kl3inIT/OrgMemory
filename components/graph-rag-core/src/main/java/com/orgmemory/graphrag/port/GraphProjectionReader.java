package com.orgmemory.graphrag.port;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.RelationContribution;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface GraphProjectionReader {

    List<EntityContribution> loadEntityContributions(
            AuthorizedEvidenceScope scope,
            Collection<UUID> entityIds);

    List<RelationContribution> loadRelationContributions(
            AuthorizedEvidenceScope scope,
            Collection<UUID> relationIds);

    List<CanonicalRelation> loadIncidentRelations(
            AuthorizedEvidenceScope scope,
            Collection<UUID> entityIds,
            int limit);

    Map<UUID, Long> loadVisibleEntityDegrees(
            AuthorizedEvidenceScope scope,
            Collection<UUID> entityIds);

    Map<UUID, Double> loadVisibleRelationWeights(
            AuthorizedEvidenceScope scope,
            Collection<UUID> relationIds);
}
