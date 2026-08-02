package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.knowledge.asset.KnowledgeAssetAuthorizationScope;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetRetrievalQuery;
import com.orgmemory.core.knowledge.acl.KnowledgeSpaceAclGenerationRef;
import com.orgmemory.core.knowledge.acl.SourceAclQuery;
import com.orgmemory.core.authorization.AuthorizedResourceQuery;
import com.orgmemory.core.authorization.AccessState;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.KnowledgeAccessSubject;
import com.orgmemory.core.organization.KnowledgeAccessSubjectQuery;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves one fail-closed OpenFGA and canonical-ledger evidence scope for all
 * permission-aware retrieval, graph and citation use cases.
 */
@Service
public class KnowledgeEvidenceScopeResolver {

    private static final PermissionKey CAN_VIEW = PermissionKey.of("can_view");
    private static final String RESOURCE_TYPE = "knowledge_asset";

    private final KnowledgeAccessSubjectQuery subjects;
    private final RelationshipAuthorizationSetPort authorization;
    private final KnowledgeAssetRetrievalQuery assets;
    private final SourceAclQuery aclQuery;
    private final SecureKnowledgeRetrievalStore canonicalEvidence;
    private final KnowledgeRetrievalProperties properties;
    private final Clock clock;

    KnowledgeEvidenceScopeResolver(
            KnowledgeAccessSubjectQuery subjects,
            RelationshipAuthorizationSetPort authorization,
            KnowledgeAssetRetrievalQuery assets,
            SourceAclQuery aclQuery,
            SecureKnowledgeRetrievalStore canonicalEvidence,
            KnowledgeRetrievalProperties properties,
            ObjectProvider<Clock> clockProvider) {
        this.subjects = subjects;
        this.authorization = authorization;
        this.assets = assets;
        this.aclQuery = aclQuery;
        this.canonicalEvidence = canonicalEvidence;
        this.properties = properties;
        this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
    }

    @Transactional(readOnly = true)
    public ResolvedKnowledgeEvidenceScope resolve(
            CurrentActor actor,
            String expectedAuthorizationModelId) {
        Objects.requireNonNull(actor, "actor");
        KnowledgeAccessSubject subject = subjects.findActive(
                        actor.organizationId(), actor.userId())
                .orElseThrow(() -> unavailable(
                        "INACTIVE_OR_UNSUPPORTED_SUBJECT",
                        expectedAuthorizationModelId));

        var listed = authorization.listAuthorizedResources(
                new AuthorizedResourceQuery(
                        actor.organizationId(),
                        actor.principal(),
                        CAN_VIEW,
                        RESOURCE_TYPE));
        if (!listed.resolved()) {
            throw unavailable(listed.reasonCode(), listed.policyVersion());
        }
        if (expectedAuthorizationModelId != null
                && !expectedAuthorizationModelId.equals(
                        listed.policyVersion())) {
            throw unavailable(
                    "AUTHORIZATION_MODEL_MISMATCH",
                    listed.policyVersion());
        }

        List<ResourceRef> resources = listed.resources().stream()
                .distinct()
                .toList();
        boolean invalidResource = resources.stream()
                .anyMatch(resource -> !actor.organizationId()
                                .equals(resource.organizationId())
                        || !RESOURCE_TYPE.equals(resource.type()));
        if (invalidResource
                || resources.size() != listed.resources().size()
                || resources.size()
                        > properties.maximumAuthorizedObjects()) {
            throw unavailable(
                    "AUTHORIZED_OBJECT_SET_INVALID",
                    listed.policyVersion());
        }

        List<UUID> listedAssetIds = new ArrayList<>(resources.size());
        try {
            for (ResourceRef resource : resources) {
                listedAssetIds.add(UUID.fromString(resource.id()));
            }
        } catch (IllegalArgumentException invalidIdentifier) {
            throw unavailable(
                    "AUTHORIZED_OBJECT_SET_INVALID",
                    listed.policyVersion());
        }

        Map<UUID, Set<UUID>> bySpace = new LinkedHashMap<>();
        if (!listedAssetIds.isEmpty()) {
            for (KnowledgeAssetAuthorizationScope asset :
                    assets.findActiveAuthorizationScopes(
                            actor.organizationId(),
                            listedAssetIds)) {
                bySpace.computeIfAbsent(
                                asset.knowledgeSpaceId(),
                                ignored -> new LinkedHashSet<>())
                        .add(asset.assetId());
            }
        }
        Instant evaluatedAt = Instant.now(clock);
        ResolvedKnowledgeEvidenceScope listedScope =
                resolvedScope(
                        actor,
                        subject,
                        listed.policyVersion(),
                        evaluatedAt,
                        bySpace);
        Set<UUID> visibleAssetIds;
        try {
            visibleAssetIds = Set.copyOf(
                    canonicalEvidence.visibleKnowledgeAssetIds(
                            retrievalScope(listedScope)));
        } catch (DataAccessException unavailable) {
            throw unavailable(
                    "CANONICAL_AUTHORIZATION_UNAVAILABLE",
                    listed.policyVersion());
        }
        if (!listedScope.allAssetIds().containsAll(visibleAssetIds)) {
            throw unavailable(
                    "CANONICAL_AUTHORIZATION_SCOPE_INVALID",
                    listed.policyVersion());
        }
        Map<UUID, Set<UUID>> visibleBySpace = new LinkedHashMap<>();
        bySpace.forEach((spaceId, assetIds) -> {
            LinkedHashSet<UUID> visible = new LinkedHashSet<>(assetIds);
            visible.retainAll(visibleAssetIds);
            if (!visible.isEmpty()) {
                visibleBySpace.put(spaceId, visible);
            }
        });
        return resolvedScope(
                actor,
                subject,
                listed.policyVersion(),
                evaluatedAt,
                visibleBySpace);
    }

    /**
     * Reuses the canonical retrieval eligibility query for one bounded, already
     * relationship-authorized asset inspected by an audit viewer.
     */
    @Transactional(readOnly = true)
    public AssetInspection inspectAsset(
            CurrentActor actor,
            UUID assetId,
            String authorizationModelId,
            Instant evaluatedAt) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        KnowledgeAccessSubject subject = subjects.findActive(
                        actor.organizationId(), actor.userId())
                .orElse(null);
        if (subject == null) {
            return new AssetInspection(AccessState.DENIED, "SUBJECT_INACTIVE");
        }
        var scope = new SecureKnowledgeRetrievalStore.RetrievalScope(
                actor.organizationId(),
                actor.userId(),
                subject.departmentId(),
                subject.executive(),
                List.of(assetId),
                authorizationModelId,
                evaluatedAt);
        try {
            boolean visible = canonicalEvidence.visibleKnowledgeAssetIds(scope).contains(assetId);
            return new AssetInspection(
                    visible ? AccessState.ALLOWED : AccessState.DENIED,
                    visible
                            ? "CANONICAL_RETRIEVAL_POLICY_ALLOWED"
                            : "CANONICAL_RETRIEVAL_POLICY_DENIED");
        } catch (DataAccessException unavailable) {
            return new AssetInspection(
                    AccessState.UNKNOWN,
                    "CANONICAL_AUTHORIZATION_UNAVAILABLE");
        }
    }

    private ResolvedKnowledgeEvidenceScope resolvedScope(
            CurrentActor actor,
            KnowledgeAccessSubject subject,
            String authorizationModelId,
            Instant evaluatedAt,
            Map<UUID, Set<UUID>> bySpace) {
        Map<UUID, Long> aclGenerations = new LinkedHashMap<>();
        bySpace.keySet().forEach(spaceId ->
                aclGenerations.put(spaceId, 0L));
        List<UUID> assetIds = bySpace.values().stream()
                .flatMap(Set::stream)
                .distinct()
                .toList();
        if (!assetIds.isEmpty()) {
            for (KnowledgeSpaceAclGenerationRef generation :
                    aclQuery.maximumCurrentAclGenerations(
                            actor.organizationId(),
                            assetIds)) {
                if (!aclGenerations.containsKey(
                        generation.knowledgeSpaceId())) {
                    throw unavailable(
                            "CANONICAL_AUTHORIZATION_SCOPE_INVALID",
                            authorizationModelId);
                }
                aclGenerations.put(
                        generation.knowledgeSpaceId(),
                        generation.aclGeneration());
            }
        }
        return new ResolvedKnowledgeEvidenceScope(
                actor.organizationId(),
                actor.userId(),
                subject.departmentId(),
                subject.executive(),
                authorizationModelId,
                evaluatedAt,
                bySpace,
                aclGenerations);
    }

    private static SecureKnowledgeRetrievalStore.RetrievalScope retrievalScope(
            ResolvedKnowledgeEvidenceScope scope) {
        return scope.toRetrievalScope();
    }

    private static KnowledgeEvidenceScopeUnavailableException unavailable(
            String reasonCode,
            String policyVersion) {
        return new KnowledgeEvidenceScopeUnavailableException(
                reasonCode == null || reasonCode.isBlank()
                        ? "AUTHORIZATION_UNAVAILABLE"
                        : reasonCode,
                policyVersion);
    }

    public record AssetInspection(AccessState state, String reasonCode) {
    }
}
