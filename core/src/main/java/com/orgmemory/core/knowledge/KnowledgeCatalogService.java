package com.orgmemory.core.knowledge;

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
public class KnowledgeCatalogService {

    private final KnowledgeEvidenceScopeResolver evidenceScopes;
    private final KnowledgeAssetVersionRepository versions;

    KnowledgeCatalogService(
            KnowledgeEvidenceScopeResolver evidenceScopes,
            KnowledgeAssetVersionRepository versions) {
        this.evidenceScopes = evidenceScopes;
        this.versions = versions;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeCatalogItem> list(CurrentActor actor) {
        Objects.requireNonNull(actor, "actor");
        ResolvedKnowledgeEvidenceScope scope = evidenceScopes.resolve(actor, null);
        if (scope.allAssetIds().isEmpty()) {
            return List.of();
        }
        return versions.findCurrentCatalogItems(
                actor.organizationId(), scope.allAssetIds());
    }

    @Transactional(readOnly = true)
    public Optional<KnowledgeCatalogItem> findExactVisible(
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
                actor.organizationId(), knowledgeAssetId, knowledgeVersionId);
    }

    @Transactional(readOnly = true)
    public Optional<KnowledgeCatalogItem> findVersionVisible(
            CurrentActor actor, UUID knowledgeVersionId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(knowledgeVersionId, "knowledgeVersionId");
        return versions.findByIdAndOrganizationId(
                        knowledgeVersionId, actor.organizationId())
                .flatMap(version -> findExactVisible(
                        actor,
                        version.getKnowledgeAssetId(),
                        knowledgeVersionId));
    }
}
