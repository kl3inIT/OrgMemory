package com.orgmemory.api.admin;

import com.orgmemory.api.ApiRequestException;
import com.orgmemory.core.authorization.AccessExplanation;
import com.orgmemory.core.authorization.AccessExplanationService;
import com.orgmemory.core.authorization.AccessState;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.PrincipalRef;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.knowledge.asset.KnowledgeAsset;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetRepository;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetVersionRepository;
import com.orgmemory.core.knowledge.space.KnowledgeSpaceQuery;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.knowledge.retrieval.AuthorizationResourceDirectory;
import com.orgmemory.core.knowledge.retrieval.KnowledgeEvidenceScopeResolver;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a user may actually do, and why.
 *
 * <p>Nothing here is stored. A permission is computed from relationships at the moment it is
 * asked, so a snapshot of one would be a second source of truth that drifts; every response
 * carries the instant it was evaluated and the authorization model it resolved against instead.
 */
@RestController
@RequestMapping("/api/admin")
class AdminPermissionController {

    /**
     * The organization-level permissions the model defines. Kept explicit rather than discovered
     * so that adding one to the model is a deliberate decision to show it to administrators.
     */
    private static final List<PermissionKey> ORGANIZATION_PERMISSIONS = List.of(
            PermissionKey.of("can_manage_members"),
            PermissionKey.of("can_manage_sources"),
            PermissionKey.of("can_manage_ai"),
            PermissionKey.of("can_create_knowledge_space"),
            PermissionKey.of("can_search_knowledge"),
            PermissionKey.of("can_view_directory"),
            PermissionKey.of("can_view_audit"),
            PermissionKey.of("can_curate_graph"));

    /** Resource types an administrator may ask about. Anything else is refused rather than guessed. */
    private static final Set<String> EXPLAINABLE_TYPES =
            Set.of("organization", "organizational_unit", "knowledge_space", "knowledge_asset");

    private final AdminAccessGuard guard;
    private final AccessExplanationService explanations;
    private final AuthorizationResourceDirectory resources;
    private final KnowledgeEvidenceScopeResolver evidenceScopes;
    private final KnowledgeAssetRepository assets;
    private final KnowledgeAssetVersionRepository versions;
    private final KnowledgeSpaceQuery spaces;

    AdminPermissionController(
            AdminAccessGuard guard,
            AccessExplanationService explanations,
            AuthorizationResourceDirectory resources,
            KnowledgeEvidenceScopeResolver evidenceScopes,
            KnowledgeAssetRepository assets,
            KnowledgeAssetVersionRepository versions,
            KnowledgeSpaceQuery spaces) {
        this.guard = guard;
        this.explanations = explanations;
        this.resources = resources;
        this.evidenceScopes = evidenceScopes;
        this.assets = assets;
        this.versions = versions;
        this.spaces = spaces;
    }

    record EffectivePermissionResponse(
            UUID userId, Map<String, AccessState> permissions, Instant evaluatedAt) {
    }

    record ExplainAccessRequest(UUID userId, String permission, String resourceType, UUID resourceId) {
    }

    record AccessStepResponse(String object, String relation, String kind) {
    }

    record AccessBlockResponse(String branch, String kind, String detail) {
    }

    record AclProvenanceResponse(
            String authority, String origin, Long generation, Instant capturedAt, boolean expired) {
    }

    record ResourceSummaryResponse(
            UUID id,
            String type,
            String label,
            String contextLabel,
            String classification) {
    }

    record ExplainAccessResponse(
            AccessState state,
            String reasonCode,
            List<AccessStepResponse> path,
            List<AccessBlockResponse> blockedBy,
            AclProvenanceResponse provenance,
            String evaluationKind,
            AccessState relationshipState,
            String relationshipReasonCode,
            AccessState contentPolicyState,
            String contentPolicyReasonCode,
            ResourceSummaryResponse resource,
            String policyVersion,
            Instant evaluatedAt) {
    }

    @GetMapping("/users/{userId}/permissions")
    @Operation(
            operationId = "listAdminUserPermissions",
            summary = "Resolve a user's organization permissions as the engine currently answers them")
    @Transactional(readOnly = true)
    EffectivePermissionResponse permissions(@PathVariable UUID userId, Authentication authentication) {
        CurrentActor actor = guard.requireMemberAdministrator(authentication);
        AppUser user = guard.requireUserInOrganization(userId, actor);
        var states = explanations.effectivePermissions(
                actor.organizationId(),
                PrincipalRef.user(user.getId()),
                ORGANIZATION_PERMISSIONS,
                ResourceRef.of(actor.organizationId(), "organization", actor.organizationId()));
        return new EffectivePermissionResponse(
                user.getId(),
                ORGANIZATION_PERMISSIONS.stream()
                        .collect(Collectors.toMap(
                                PermissionKey::value,
                                permission -> states.getOrDefault(permission, AccessState.UNKNOWN),
                                (first, second) -> first,
                                LinkedHashMap::new)),
                Instant.now());
    }

    @PostMapping("/access/explain")
    @Operation(
            operationId = "explainAdminAccess",
            summary = "Answer whether a user holds a permission on one resource, and by which derivation")
    @Transactional(readOnly = true)
    ExplainAccessResponse explain(
            @RequestBody ExplainAccessRequest request, Authentication authentication) {
        CurrentActor actor = guard.requireAuditViewer(authentication);
        AppUser user = guard.requireUserInOrganization(
                request.userId(), actor);
        if (request.permission() == null || request.permission().isBlank()) {
            throw new ApiRequestException("A permission is required");
        }
        if (request.resourceType() == null || !EXPLAINABLE_TYPES.contains(request.resourceType())) {
            throw new ApiRequestException("Unsupported resource type for an access explanation");
        }
        if (request.resourceId() == null) {
            throw new ApiRequestException("A resource id is required");
        }
        ResourceRef resource = resources.require(
                actor.organizationId(),
                request.resourceType(),
                request.resourceId());
        AccessExplanation explanation = explanations.explain(
                actor.organizationId(),
                PrincipalRef.user(user.getId()),
                PermissionKey.of(request.permission()),
                resource);
        if ("knowledge_asset".equals(request.resourceType())
                && "can_view".equals(request.permission())) {
            CurrentActor subject = new CurrentActor(
                    user.getId(),
                    user.getOrganizationId(),
                    user.getDepartmentId(),
                    user.getName(),
                    user.getEmail(),
                    user.getRole());
            return canonicalContentResponse(
                    explanation,
                    subject,
                    request.resourceId());
        }
        return relationshipOnlyResponse(explanation);
    }

    private ExplainAccessResponse canonicalContentResponse(
            AccessExplanation relationship,
            CurrentActor subject,
            UUID assetId) {
        AccessState finalState;
        String reasonCode;
        AccessState contentState;
        String contentReason;
        ResourceSummaryResponse resource = resourceSummary(subject.organizationId(), assetId);
        if (relationship.state() != AccessState.ALLOWED) {
            finalState = relationship.state();
            reasonCode = relationship.reasonCode();
            contentState = AccessState.UNKNOWN;
            contentReason = "NOT_EVALUATED_RELATIONSHIP_NOT_ALLOWED";
        } else {
            KnowledgeEvidenceScopeResolver.AssetInspection content = evidenceScopes.inspectAsset(
                    subject,
                    assetId,
                    relationship.policyVersion(),
                    relationship.evaluatedAt());
            finalState = content.state();
            contentState = content.state();
            contentReason = content.reasonCode();
            reasonCode = switch (content.state()) {
                case ALLOWED -> "EFFECTIVE_ACCESS_ALLOWED";
                case DENIED -> "CONTENT_POLICY_DENIED";
                case UNKNOWN -> "CONTENT_POLICY_UNKNOWN";
            };
        }
        return response(
                relationship,
                finalState,
                reasonCode,
                "CANONICAL_CONTENT",
                contentState,
                contentReason,
                resource);
    }

    private ResourceSummaryResponse resourceSummary(UUID organizationId, UUID assetId) {
        KnowledgeAsset asset = assets.findByIdAndOrganizationId(assetId, organizationId)
                .orElseThrow();
        String label = "Document";
        String classification = null;
        if (asset.getCurrentVersionId() != null) {
            var current = versions.findCurrentCatalogItem(
                    organizationId,
                    assetId,
                    asset.getCurrentVersionId());
            if (current.isPresent()) {
                label = current.get().title();
                classification = current.get().classification().name();
            }
        }
        String contextLabel = spaces.findName(organizationId, asset.getKnowledgeSpaceId())
                .orElse("Knowledge space");
        return new ResourceSummaryResponse(
                assetId,
                "knowledge_asset",
                label,
                contextLabel,
                classification);
    }

    private static ExplainAccessResponse relationshipOnlyResponse(AccessExplanation explanation) {
        return response(
                explanation,
                explanation.state(),
                explanation.reasonCode(),
                "RELATIONSHIP_ONLY",
                null,
                null,
                null);
    }

    private static ExplainAccessResponse response(
            AccessExplanation explanation,
            AccessState finalState,
            String reasonCode,
            String evaluationKind,
            AccessState contentPolicyState,
            String contentPolicyReasonCode,
            ResourceSummaryResponse resource) {
        return new ExplainAccessResponse(
                finalState,
                reasonCode,
                explanation.path().stream()
                        .map(step -> new AccessStepResponse(
                                step.object(), step.relation(), step.kind().name()))
                        .toList(),
                explanation.blockedBy().stream()
                        .map(block -> new AccessBlockResponse(
                                block.branch(), block.kind().name(), block.detail()))
                        .toList(),
                new AclProvenanceResponse(
                        explanation.provenance().authority(),
                        explanation.provenance().origin(),
                        explanation.provenance().generation(),
                        explanation.provenance().capturedAt(),
                        explanation.provenance().expired()),
                evaluationKind,
                explanation.state(),
                explanation.reasonCode(),
                contentPolicyState,
                contentPolicyReasonCode,
                resource,
                explanation.policyVersion(),
                explanation.evaluatedAt());
    }
}
