package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.knowledge.asset.KnowledgeAssetVersionRepository;
import com.orgmemory.core.knowledge.asset.KnowledgeCatalogItem;
import com.orgmemory.core.knowledge.catalog.KnowledgeCatalogEntry;
import com.orgmemory.core.knowledge.catalog.KnowledgeCatalogQuery;
import com.orgmemory.core.organization.CurrentActor;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only federation into the canonical Knowledge ledger. This service never
 * creates registry Asset rows or copies Knowledge authorization tuples.
 */
@Service
public class KnowledgeCatalogService implements KnowledgeCatalogQuery {

    private final KnowledgeEvidenceScopeResolver evidenceScopes;
    private final KnowledgeAssetVersionRepository versions;

    KnowledgeCatalogService(
            KnowledgeEvidenceScopeResolver evidenceScopes,
            KnowledgeAssetVersionRepository versions) {
        this.evidenceScopes = evidenceScopes;
        this.versions = versions;
    }

    @Transactional(readOnly = true)
    @Override
    public List<KnowledgeCatalogEntry> list(CurrentActor actor) {
        Objects.requireNonNull(actor, "actor");
        ResolvedKnowledgeEvidenceScope scope = evidenceScopes.resolve(actor, null);
        if (scope.allAssetIds().isEmpty()) {
            return List.of();
        }
        return versions.findCurrentCatalogItems(
                        actor.organizationId(), scope.allAssetIds())
                .stream()
                .map(KnowledgeCatalogService::toEntry)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<KnowledgeCatalogEntry> findExactVisible(
            CurrentActor actor,
            UUID knowledgeAssetId,
            UUID knowledgeVersionId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId");
        Objects.requireNonNull(knowledgeVersionId, "knowledgeVersionId");
        ResolvedKnowledgeEvidenceScope scope = evidenceScopes.resolve(actor, null);
        if (!scope.allAssetIds().contains(knowledgeAssetId)) {
            return Optional.empty();
        }
        return versions.findCurrentCatalogItem(
                        actor.organizationId(), knowledgeAssetId, knowledgeVersionId)
                .map(KnowledgeCatalogService::toEntry);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<KnowledgeCatalogEntry> findVersionVisible(
            CurrentActor actor, UUID knowledgeVersionId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(knowledgeVersionId, "knowledgeVersionId");
        ResolvedKnowledgeEvidenceScope scope = evidenceScopes.resolve(actor, null);
        if (scope.allAssetIds().isEmpty()) {
            return Optional.empty();
        }
        return versions.findCurrentCatalogItemByVersion(
                        actor.organizationId(),
                        knowledgeVersionId,
                        scope.allAssetIds())
                .map(KnowledgeCatalogService::toEntry);
    }

    private static KnowledgeCatalogEntry toEntry(KnowledgeCatalogItem item) {
        return new KnowledgeCatalogEntry(
                item.knowledgeAssetId(),
                item.knowledgeVersionId(),
                item.versionNumber(),
                item.knowledgeSpaceId(),
                item.title(),
                item.language(),
                item.classification(),
                item.contentDigest());
    }
}
