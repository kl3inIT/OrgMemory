package com.orgmemory.core.knowledge.graph;

import com.orgmemory.core.knowledge.retrieval.GraphEvidenceVerifier;
import com.orgmemory.core.knowledge.asset.KnowledgeProjectionNamespaces;
import com.orgmemory.core.shared.error.KnowledgeResourceNotFoundException;
import com.orgmemory.core.knowledge.retrieval.KnowledgeRetrievalUnavailableException;
import com.orgmemory.core.knowledge.retrieval.VerifiedGraphEvidenceScope;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetGraphQuery;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.knowledge.space.KnowledgeSpaceQuery;
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

    private final KnowledgeSpaceQuery spaces;
    private final KnowledgeAssetGraphQuery assets;
    private final RelationshipAuthorizationPort authorization;
    private final GraphEvidenceVerifier evidenceVerifier;
    private final GraphExportReader graphs;
    private final GraphCurationStore curations;
    private final ModelInvocationCache modelCache;
    private final RetrievalResultCache retrievalCache;

    KnowledgeGraphCurationService(
            KnowledgeSpaceQuery spaces,
            KnowledgeAssetGraphQuery assets,
            RelationshipAuthorizationPort authorization,
            GraphEvidenceVerifier evidenceVerifier,
            GraphExportReader graphs,
            GraphCurationStore curations,
            ModelInvocationCache modelCache,
            RetrievalResultCache retrievalCache) {
        this.spaces = spaces;
        this.assets = assets;
        this.authorization = authorization;
        this.evidenceVerifier = evidenceVerifier;
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
        VerifiedGraphEvidenceScope resolved =
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
        VerifiedGraphEvidenceScope resolved =
                resolve(actor, decision.policyVersion());
        if (resolved.authorizationGeneration(knowledgeSpaceId)
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
            VerifiedGraphEvidenceScope resolved) {
        if (!actor.organizationId().equals(evidence.organizationId())) {
            throw new KnowledgeResourceNotFoundException();
        }
        assets.requireInSpace(
                actor.organizationId(), evidence.knowledgeAssetId(), knowledgeSpaceId);
        if (!resolved.includes(
                knowledgeSpaceId,
                evidence.organizationId(), evidence.knowledgeAssetId())) {
            throw new OrgMemoryAccessDeniedException(
                    "Governing evidence is not visible to the current actor");
        }
        if (!evidenceVerifier.isCurrentGoverningEvidence(
                resolved, knowledgeSpaceId, evidence)) {
            throw new OrgMemoryAccessDeniedException(
                    "Governing evidence is stale or unavailable");
        }
    }

    private void requireCurrentScope(
            KnowledgeGraphCurationCommand command,
            VerifiedGraphEvidenceScope resolved) {
        UUID spaceId = command.knowledgeSpaceId();
        if (!resolved.includesKnowledgeSpace(spaceId)
                || resolved.authorizationGeneration(spaceId)
                        != command.authorizationGeneration()) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Knowledge graph authorization changed before curation");
        }
    }

    private void requireVisibleEntity(
            VerifiedGraphEvidenceScope resolved,
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
            VerifiedGraphEvidenceScope resolved,
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

    private VerifiedGraphEvidenceScope resolve(
            CurrentActor actor,
            String authorizationModelId) {
        try {
            return evidenceVerifier.verifyScope(actor, authorizationModelId);
        } catch (KnowledgeRetrievalUnavailableException unavailable) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Knowledge graph permissions are temporarily unavailable",
                    unavailable);
        }
    }

    private void requireUnchangedScope(
            CurrentActor actor,
            UUID knowledgeSpaceId,
            String authorizationModelId,
            VerifiedGraphEvidenceScope initial) {
        VerifiedGraphEvidenceScope current =
                resolve(actor, authorizationModelId);
        if (!initial.hasSameAssetsAndGeneration(current, knowledgeSpaceId)) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Knowledge graph authorization changed during curation");
        }
    }

    private void requireSpace(CurrentActor actor, UUID knowledgeSpaceId) {
        if (!spaces.isActive(actor.organizationId(), knowledgeSpaceId)) {
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
        return KnowledgeProjectionNamespaces.forSpace(organizationId, knowledgeSpaceId);
    }
}
