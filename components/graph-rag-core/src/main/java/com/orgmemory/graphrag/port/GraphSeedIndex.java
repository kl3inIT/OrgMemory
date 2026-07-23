package com.orgmemory.graphrag.port;

import com.orgmemory.graphrag.authorization.AuthorizedGraphScope;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.query.RankedItem;
import java.util.List;

public interface GraphSeedIndex {

    List<RankedItem<CanonicalEntity>> searchEntities(
            AuthorizedGraphScope scope,
            String query,
            int limit);

    List<RankedItem<CanonicalRelation>> searchRelations(
            AuthorizedGraphScope scope,
            String query,
            int limit);
}
