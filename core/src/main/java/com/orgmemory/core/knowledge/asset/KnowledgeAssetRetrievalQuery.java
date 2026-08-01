package com.orgmemory.core.knowledge.asset;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Asset-owned read boundary for permission-aware retrieval and catalog federation.
 *
 * <p>Implementations enforce organization ownership plus the active/current lifecycle predicates
 * represented by each method.
 */
public interface KnowledgeAssetRetrievalQuery {

    boolean exists(UUID organizationId, UUID knowledgeAssetId);

    List<KnowledgeAssetAuthorizationScope> findActiveAuthorizationScopes(
            UUID organizationId,
            Collection<UUID> knowledgeAssetIds);

    List<KnowledgeCatalogItem> findCurrentCatalogItems(
            UUID organizationId,
            Collection<UUID> authorizedKnowledgeAssetIds);

    Optional<KnowledgeCatalogItem> findCurrentCatalogItem(
            UUID organizationId,
            UUID knowledgeAssetId,
            UUID knowledgeVersionId);

    Optional<KnowledgeCatalogItem> findCurrentCatalogItemByVersion(
            UUID organizationId,
            UUID knowledgeVersionId,
            Collection<UUID> authorizedKnowledgeAssetIds);
}
