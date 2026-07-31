package com.orgmemory.core.knowledge;

import com.orgmemory.core.knowledge.asset.KnowledgeAsset;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetNotFoundException;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetRepository;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.knowledge.space.KnowledgeSpaceRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.cache.RetrievalResultCache;
import com.orgmemory.graphrag.curation.CurationProvenance;
import com.orgmemory.graphrag.curation.GraphCurationRecord;
import com.orgmemory.graphrag.curation.GraphCurationStore;
import com.orgmemory.graphrag.curation.GraphIdentityRef;
import com.orgmemory.graphrag.export.GraphExportDocument;
import com.orgmemory.graphrag.export.GraphExportReader;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Permission-checked use case for append-only graph create/edit/merge/delete. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class KnowledgeGraphCurationService {

    private static final PermissionKey CAN_CURATE_GRAPH =
            PermissionKey.of("can_curate_graph");
    private static final String RESOURCE_TYPE = "knowledge_space";

    private final KnowledgeSpaceRepository spaces;
    private final KnowledgeAssetRepository assets;
    private final RelationshipAuthorizationPort authorization;
    private final KnowledgeEvidenceScopeResolver evidenceScopes;
    private final SecureKnowledgeRetrievalStore canonicalEvidence;
    private final GraphExportReader graphs;
    private final GraphCurationStore curations;
    private final ModelInvocationCache modelCache;
    private final RetrievalResultCache retrievalCache;

    KnowledgeGraphCurationService(
            KnowledgeSpaceRepository spaces,
            KnowledgeAssetRepository assets,
            RelationshipAuthorizationPort authorization,
            KnowledgeEvidenceScopeResolver evidenceScopes,
            SecureKnowledgeRetrievalStore canonicalEvidence,
            GraphExportReader graphs,
            GraphCurationStore curations,
            ModelInvocationCache modelCache,
            RetrievalResultCache retrievalCache) {
        this.spaces = spaces;
        this.assets = assets;
        this.authorization = authorization;
        this.evidenceScopes = evidenceScopes;
        this.canonicalEvidence = canonicalEvidence;
        this.graphs = graphs;
        this.curations = curations;
        this.modelCache = modelCache;
        this.retrievalCache = retrievalCache;
    }

    @Transactional
    public GraphCurationRecord apply(
            CurrentActor actor, KnowledgeGraphCurationCommand command) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(command, "command");
        requireSpace(actor, command.knowledgeSpaceId());
        AuthorizationDecision decision = requirePermission(
                actor, command.knowledgeSpaceId());
        ProjectionNamespace namespace =
                namespace(actor.organizationId(), command.knowledgeSpaceId());
        ResolvedKnowledgeEvidenceScope resolved =
                resolve(actor, decision.policyVersion());
        requireCurrentScope(command, resolved);
        CurationProvenance provenance = new CurationProvenance(
                actor.userId(),
                decision.policyVersion(),
                command.authorizationGeneration(),
                Instant.now(),
                command.reason());
        GraphCurationRecord record = switch (command) {
            case KnowledgeGraphCurationCommand.CurateEntity entity -> {
                requireGoverningEvidence(
                        actor,
                        command.knowledgeSpaceId(),
                        entity.governingEvidence(),
                        resolved);
                yield new GraphCurationRecord.CuratedEntity(
                        UUID.randomUUID(),
                        namespace,
                        GraphIdentityRef.entity(entity.entityId()),
                        entity.name(),
                        entity.type(),
                        entity.description(),
                        entity.governingEvidence(),
                        provenance);
            }
            case KnowledgeGraphCurationCommand.CurateRelation relation -> {
                requireGoverningEvidence(
                        actor,
                        command.knowledgeSpaceId(),
                        relation.governingEvidence(),
                        resolved);
                requireVisibleEntity(
                        resolved,
                        namespace,
                        command.knowledgeSpaceId(),
                        relation.sourceEntityId());
                requireVisibleEntity(
                        resolved,
                        namespace,
                        command.knowledgeSpaceId(),
                        relation.targetEntityId());
                yield new GraphCurationRecord.CuratedRelation(
                        UUID.randomUUID(),
                        namespace,
                        GraphIdentityRef.relation(relation.relationId()),
                        GraphIdentityRef.entity(relation.sourceEntityId()),
                        GraphIdentityRef.entity(relation.targetEntityId()),
                        relation.type(),
                        relation.keywords(),
                        relation.description(),
                        relation.weight(),
                        relation.governingEvidence(),
                        provenance);
            }
            case KnowledgeGraphCurationCommand.AliasIdentity alias -> {
                requireVisibleIdentity(
                        resolved,
                        namespace,
                        command.knowledgeSpaceId(),
                        alias.kind(),
                        alias.sourceIdentityId());
                requireVisibleIdentity(
                        resolved,
                        namespace,
                        command.knowledgeSpaceId(),
                        alias.kind(),
                        alias.targetIdentityId());
                yield new GraphCurationRecord.IdentityAlias(
                            UUID.randomUUID(),
                            namespace,
                            new GraphIdentityRef(
                                    alias.kind(), alias.sourceIdentityId()),
                            new GraphIdentityRef(
                                    alias.kind(), alias.targetIdentityId()),
                            provenance);
            }
            case KnowledgeGraphCurationCommand.SuppressIdentity suppression -> {
                requireVisibleIdentity(
                        resolved,
                        namespace,
                        command.knowledgeSpaceId(),
                        suppression.kind(),
                        suppression.identityId());
                yield new GraphCurationRecord.IdentitySuppression(
                            UUID.randomUUID(),
                            namespace,
                            new GraphIdentityRef(
                                    suppression.kind(),
                                    suppression.identityId()),
                            provenance);
            }
        };
        GraphCurationRecord stored =
                curations.append(command.idempotencyKey(), record);
        requireUnchangedScope(
                actor,
                command.knowledgeSpaceId(),
                decision.policyVersion(),
                resolved);
        invalidate(namespace);
        return stored;
    }

    @Transactional
    public void deactivate(
            CurrentActor actor,
            UUID knowledgeSpaceId,
            UUID recordId,
            long authorizationGeneration,
            String reason) {
        Objects.requireNonNull(actor, "actor");
        requireSpace(actor, knowledgeSpaceId);
        AuthorizationDecision decision =
                requirePermission(actor, knowledgeSpaceId);
        ResolvedKnowledgeEvidenceScope resolved =
                resolve(actor, decision.policyVersion());
        if (resolved.aclGenerationByKnowledgeSpace()
                        .getOrDefault(knowledgeSpaceId, 0L)
                != authorizationGeneration) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Knowledge graph authorization changed before curation");
        }
        ProjectionNamespace namespace =
                namespace(actor.organizationId(), knowledgeSpaceId);
        curations.deactivate(
                namespace,
                Objects.requireNonNull(recordId, "recordId"),
                new CurationProvenance(
                        actor.userId(),
                        decision.policyVersion(),
                        authorizationGeneration,
                        Instant.now(),
                        reason));
        requireUnchangedScope(
                actor,
                knowledgeSpaceId,
                decision.policyVersion(),
                resolved);
        invalidate(namespace);
    }

    private void requireGoverningEvidence(
            CurrentActor actor,
            UUID knowledgeSpaceId,
            com.orgmemory.graphrag.model.EvidenceReference evidence,
            ResolvedKnowledgeEvidenceScope resolved) {
        if (!actor.organizationId().equals(evidence.organizationId())) {
            throw new KnowledgeResourceNotFoundException();
        }
        KnowledgeAsset asset = assets
                .findByIdAndOrganizationId(
                        evidence.knowledgeAssetId(), actor.organizationId())
                .orElseThrow(KnowledgeAssetNotFoundException::new);
        if (!knowledgeSpaceId.equals(asset.getKnowledgeSpaceId())) {
            throw new KnowledgeResourceNotFoundException();
        }
        var spaceScope = resolved.forKnowledgeSpace(knowledgeSpaceId);
        if (!spaceScope.includes(
                evidence.organizationId(), evidence.knowledgeAssetId())) {
            throw new OrgMemoryAccessDeniedException(
                    "Governing evidence is not visible to the current actor");
        }
        var candidates = canonicalEvidence.recheck(
                retrievalScope(resolved),
                java.util.List.of(Objects.requireNonNull(
                        evidence.chunkId(), "governing evidence chunkId")));
        boolean current = candidates.size() == 1
                && candidates.getFirst().knowledgeAssetId()
                        .equals(evidence.knowledgeAssetId())
                && candidates.getFirst().sourceRevisionId()
                        .equals(evidence.sourceRevisionId())
                && candidates.getFirst().currentAclSnapshotId()
                        .equals(evidence.aclSnapshotId());
        if (!current) {
            throw new OrgMemoryAccessDeniedException(
                    "Governing evidence is stale or unavailable");
        }
    }

    private void requireCurrentScope(
            KnowledgeGraphCurationCommand command,
            ResolvedKnowledgeEvidenceScope resolved) {
        UUID spaceId = command.knowledgeSpaceId();
        if (!resolved.knowledgeSpaceIds().contains(spaceId)
                || resolved.aclGenerationByKnowledgeSpace()
                                .getOrDefault(spaceId, 0L)
                        != command.authorizationGeneration()) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Knowledge graph authorization changed before curation");
        }
    }

    private void requireVisibleEntity(
            ResolvedKnowledgeEvidenceScope resolved,
            ProjectionNamespace namespace,
            UUID knowledgeSpaceId,
            UUID entityId) {
        requireVisibleIdentity(
                resolved,
                namespace,
                knowledgeSpaceId,
                com.orgmemory.graphrag.curation.GraphIdentityKind.ENTITY,
                entityId);
    }

    private void requireVisibleIdentity(
            ResolvedKnowledgeEvidenceScope resolved,
            ProjectionNamespace namespace,
            UUID knowledgeSpaceId,
            com.orgmemory.graphrag.curation.GraphIdentityKind kind,
            UUID identityId) {
        GraphExportDocument document = graphs.read(
                resolved.forKnowledgeSpace(knowledgeSpaceId),
                namespace);
        boolean visible = switch (kind) {
            case ENTITY -> document.entities().stream()
                    .anyMatch(entity -> entity.id().equals(identityId));
            case RELATION -> document.relations().stream()
                    .anyMatch(relation -> relation.id().equals(identityId));
        };
        if (!visible) {
            throw new OrgMemoryAccessDeniedException(
                    "Graph identity is not visible to the current actor");
        }
    }

    private ResolvedKnowledgeEvidenceScope resolve(
            CurrentActor actor,
            String authorizationModelId) {
        try {
            return evidenceScopes.resolve(actor, authorizationModelId);
        } catch (KnowledgeEvidenceScopeUnavailableException unavailable) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Knowledge graph permissions are temporarily unavailable");
        }
    }

    private void requireUnchangedScope(
            CurrentActor actor,
            UUID knowledgeSpaceId,
            String authorizationModelId,
            ResolvedKnowledgeEvidenceScope initial) {
        ResolvedKnowledgeEvidenceScope current =
                resolve(actor, authorizationModelId);
        if (!initial.forKnowledgeSpace(knowledgeSpaceId)
                        .authorizedAssetIds()
                        .equals(current.forKnowledgeSpace(knowledgeSpaceId)
                                .authorizedAssetIds())
                || initial.aclGenerationByKnowledgeSpace()
                                .getOrDefault(knowledgeSpaceId, 0L)
                        != current.aclGenerationByKnowledgeSpace()
                                .getOrDefault(knowledgeSpaceId, 0L)) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Knowledge graph authorization changed during curation");
        }
    }

    private static SecureKnowledgeRetrievalStore.RetrievalScope retrievalScope(
            ResolvedKnowledgeEvidenceScope scope) {
        return new SecureKnowledgeRetrievalStore.RetrievalScope(
                scope.organizationId(),
                scope.actorUserId(),
                scope.actorDepartmentId(),
                scope.actorExecutive(),
                scope.allAssetIds().stream().sorted().toList(),
                scope.authorizationModelId(),
                scope.evaluatedAt());
    }

    private void requireSpace(CurrentActor actor, UUID knowledgeSpaceId) {
        if (!spaces.existsByIdAndOrganizationIdAndActiveTrue(
                knowledgeSpaceId, actor.organizationId())) {
            throw new OrgMemoryAccessDeniedException(
                    "Knowledge Space is unavailable");
        }
    }

    private AuthorizationDecision requirePermission(
            CurrentActor actor, UUID knowledgeSpaceId) {
        AuthorizationDecision decision =
                authorization.check(new RelationshipAuthorizationQuery(
                        actor.principal(),
                        CAN_CURATE_GRAPH,
                        ResourceRef.of(
                                actor.organizationId(),
                                RESOURCE_TYPE,
                                knowledgeSpaceId)));
        if (!decision.allowed()) {
            throw new OrgMemoryAccessDeniedException(
                    "The current user is not authorized to curate this graph");
        }
        return decision;
    }

    private void invalidate(ProjectionNamespace namespace) {
        modelCache.invalidate(namespace);
        retrievalCache.invalidateNamespace(namespace);
    }

    private static ProjectionNamespace namespace(
            UUID organizationId, UUID knowledgeSpaceId) {
        return new ProjectionNamespace(
                organizationId, "default", knowledgeSpaceId.toString());
    }
}
