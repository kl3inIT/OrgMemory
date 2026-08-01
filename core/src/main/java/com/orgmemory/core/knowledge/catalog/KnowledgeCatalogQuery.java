package com.orgmemory.core.knowledge.catalog;

import com.orgmemory.core.organization.CurrentActor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Permission-aware read access to current Knowledge catalog entries. */
public interface KnowledgeCatalogQuery {

    List<KnowledgeCatalogEntry> list(CurrentActor actor);

    Optional<KnowledgeCatalogEntry> findExactVisible(
            CurrentActor actor,
            UUID knowledgeAssetId,
            UUID knowledgeVersionId);

    Optional<KnowledgeCatalogEntry> findVersionVisible(
            CurrentActor actor,
            UUID knowledgeVersionId);
}
