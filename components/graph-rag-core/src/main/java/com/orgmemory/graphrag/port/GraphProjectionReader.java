package com.orgmemory.graphrag.port;

import com.orgmemory.graphrag.authorization.AuthorizedGraphScope;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.RelationContribution;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface GraphProjectionReader {

    List<EntityContribution> loadEntityContributions(
            AuthorizedGraphScope scope,
            Collection<UUID> entityIds);

    List<RelationContribution> loadRelationContributions(
            AuthorizedGraphScope scope,
            Collection<UUID> relationIds);

    List<CanonicalRelation> loadIncidentRelations(
            AuthorizedGraphScope scope,
            Collection<UUID> entityIds,
            int limit);

    Map<UUID, Long> loadVisibleEntityDegrees(
            AuthorizedGraphScope scope,
            Collection<UUID> entityIds);

    Map<UUID, Double> loadVisibleRelationWeights(
            AuthorizedGraphScope scope,
            Collection<UUID> relationIds);
}
