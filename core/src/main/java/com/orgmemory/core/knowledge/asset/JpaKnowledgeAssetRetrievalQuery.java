package com.orgmemory.core.knowledge.asset;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class JpaKnowledgeAssetRetrievalQuery implements KnowledgeAssetRetrievalQuery {

    private final KnowledgeAssetRepository assets;
    private final KnowledgeAssetVersionRepository versions;

    JpaKnowledgeAssetRetrievalQuery(
            KnowledgeAssetRepository assets,
            KnowledgeAssetVersionRepository versions) {
        this.assets = assets;
        this.versions = versions;
    }

    @Override
    public boolean exists(UUID organizationId, UUID knowledgeAssetId) {
        return assets.existsByIdAndOrganizationId(
                Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId"),
                Objects.requireNonNull(organizationId, "organizationId"));
    }

    @Override
    public List<KnowledgeAssetAuthorizationScope> findActiveAuthorizationScopes(
            UUID organizationId,
            Collection<UUID> knowledgeAssetIds) {
        UUID tenantId = Objects.requireNonNull(organizationId, "organizationId");
        List<UUID> ids = immutableIds(knowledgeAssetIds, "knowledgeAssetIds");
        if (ids.isEmpty()) {
            return List.of();
        }
        return List.copyOf(assets.findActiveAuthorizationScopes(
                tenantId,
                ids));
    }

    @Override
    public List<KnowledgeCatalogItem> findCurrentCatalogItems(
            UUID organizationId,
            Collection<UUID> authorizedKnowledgeAssetIds) {
        UUID tenantId = Objects.requireNonNull(organizationId, "organizationId");
        List<UUID> ids = immutableIds(
                authorizedKnowledgeAssetIds,
                "authorizedKnowledgeAssetIds");
        if (ids.isEmpty()) {
            return List.of();
        }
        return List.copyOf(versions.findCurrentCatalogItems(
                tenantId,
                ids));
    }

    @Override
    public Optional<KnowledgeCatalogItem> findCurrentCatalogItem(
            UUID organizationId,
            UUID knowledgeAssetId,
            UUID knowledgeVersionId) {
        return versions.findCurrentCatalogItem(
                Objects.requireNonNull(organizationId, "organizationId"),
                Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId"),
                Objects.requireNonNull(knowledgeVersionId, "knowledgeVersionId"));
    }

    @Override
    public Optional<KnowledgeCatalogItem> findCurrentCatalogItemByVersion(
            UUID organizationId,
            UUID knowledgeVersionId,
            Collection<UUID> authorizedKnowledgeAssetIds) {
        UUID tenantId = Objects.requireNonNull(organizationId, "organizationId");
        UUID versionId = Objects.requireNonNull(knowledgeVersionId, "knowledgeVersionId");
        List<UUID> ids = immutableIds(
                authorizedKnowledgeAssetIds,
                "authorizedKnowledgeAssetIds");
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return versions.findCurrentCatalogItemByVersion(
                tenantId,
                versionId,
                ids);
    }

    private static List<UUID> immutableIds(
            Collection<UUID> ids,
            String name) {
        return List.copyOf(Objects.requireNonNull(ids, name));
    }
}
