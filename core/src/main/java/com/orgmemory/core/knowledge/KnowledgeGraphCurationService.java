package com.orgmemory.core.knowledge;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.cache.RetrievalResultCache;
import com.orgmemory.graphrag.curation.CurationProvenance;
import com.orgmemory.graphrag.curation.GraphCurationRecord;
import com.orgmemory.graphrag.curation.GraphCurationStore;
import com.orgmemory.graphrag.curation.GraphIdentityRef;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Permission-checked use case for append-only graph create/edit/merge/delete. */
@Service
public class KnowledgeGraphCurationService {

    private static final PermissionKey CAN_CURATE_GRAPH =
            PermissionKey.of("can_curate_graph");
    private static final String RESOURCE_TYPE = "knowledge_space";

    private final KnowledgeSpaceRepository spaces;
    private final KnowledgeAssetRepository assets;
    private final RelationshipAuthorizationPort authorization;
    private final GraphCurationStore curations;
    private final ModelInvocationCache modelCache;
    private final RetrievalResultCache retrievalCache;

    KnowledgeGraphCurationService(
            KnowledgeSpaceRepository spaces,
            KnowledgeAssetRepository assets,
            RelationshipAuthorizationPort authorization,
            GraphCurationStore curations,
            ModelInvocationCache modelCache,
            RetrievalResultCache retrievalCache) {
        this.spaces = spaces;
        this.assets = assets;
        this.authorization = authorization;
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
        CurationProvenance provenance = new CurationProvenance(
                actor.userId(),
                decision.policyVersion(),
                command.authorizationGeneration(),
                Instant.now(),
                command.reason());
        GraphCurationRecord record = switch (command) {
            case KnowledgeGraphCurationCommand.CurateEntity entity -> {
                requireGoverningEvidence(
                        actor, command.knowledgeSpaceId(), entity.governingEvidence());
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
                        relation.governingEvidence());
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
            case KnowledgeGraphCurationCommand.AliasIdentity alias ->
                    new GraphCurationRecord.IdentityAlias(
                            UUID.randomUUID(),
                            namespace,
                            new GraphIdentityRef(
                                    alias.kind(), alias.sourceIdentityId()),
                            new GraphIdentityRef(
                                    alias.kind(), alias.targetIdentityId()),
                            provenance);
            case KnowledgeGraphCurationCommand.SuppressIdentity suppression ->
                    new GraphCurationRecord.IdentitySuppression(
                            UUID.randomUUID(),
                            namespace,
                            new GraphIdentityRef(
                                    suppression.kind(),
                                    suppression.identityId()),
                            provenance);
        };
        GraphCurationRecord stored =
                curations.append(command.idempotencyKey(), record);
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
        invalidate(namespace);
    }

    private void requireGoverningEvidence(
            CurrentActor actor,
            UUID knowledgeSpaceId,
            com.orgmemory.graphrag.model.EvidenceReference evidence) {
        if (!actor.organizationId().equals(evidence.organizationId())) {
            throw new IllegalArgumentException(
                    "governing evidence belongs to another organization");
        }
        KnowledgeAsset asset = assets
                .findByIdAndOrganizationId(
                        evidence.knowledgeAssetId(), actor.organizationId())
                .orElseThrow(KnowledgeAssetNotFoundException::new);
        if (!knowledgeSpaceId.equals(asset.getKnowledgeSpaceId())) {
            throw new IllegalArgumentException(
                    "governing evidence belongs to another Knowledge Space");
        }
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
