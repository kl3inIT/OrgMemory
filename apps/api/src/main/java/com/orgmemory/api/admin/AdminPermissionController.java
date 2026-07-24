package com.orgmemory.api.admin;

import com.orgmemory.core.authorization.AccessExplanation;
import com.orgmemory.core.authorization.AccessExplanationService;
import com.orgmemory.core.authorization.AccessState;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.PrincipalRef;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.knowledge.AuthorizationResourceDirectory;
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
            PermissionKey.of("can_create_knowledge_space"),
            PermissionKey.of("can_search_knowledge"),
            PermissionKey.of("can_view_directory"),
            PermissionKey.of("can_view_audit"),
            PermissionKey.of("can_curate_graph"));

    /** Resource types an administrator may ask about. Anything else is refused rather than guessed. */
    private static final Set<String> EXPLAINABLE_TYPES =
            Set.of("organization", "organizational_unit", "knowledge_space", "knowledge_asset");

    private final AdminAccessGuard guard;
    private final AppUserRepository users;
    private final AccessExplanationService explanations;
    private final AuthorizationResourceDirectory resources;

    AdminPermissionController(
            AdminAccessGuard guard,
            AppUserRepository users,
            AccessExplanationService explanations,
            AuthorizationResourceDirectory resources) {
        this.guard = guard;
        this.users = users;
        this.explanations = explanations;
        this.resources = resources;
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

    record ExplainAccessResponse(
            AccessState state,
            String reasonCode,
            List<AccessStepResponse> path,
            List<AccessBlockResponse> blockedBy,
            AclProvenanceResponse provenance,
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
        AppUser user = requireUserInOrganization(userId, actor);
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
        CurrentActor actor = guard.requireMemberAdministrator(authentication);
        AppUser user = requireUserInOrganization(request.userId(), actor);
        if (request.permission() == null || request.permission().isBlank()) {
            throw new IllegalArgumentException("A permission is required");
        }
        if (request.resourceType() == null || !EXPLAINABLE_TYPES.contains(request.resourceType())) {
            throw new IllegalArgumentException("Unsupported resource type for an access explanation");
        }
        if (request.resourceId() == null) {
            throw new IllegalArgumentException("A resource id is required");
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
        return response(explanation);
    }

    private AppUser requireUserInOrganization(UUID userId, CurrentActor actor) {
        if (userId == null) {
            throw new IllegalArgumentException("A user id is required");
        }
        return users.findById(userId)
                .filter(candidate -> candidate.getOrganizationId().equals(actor.organizationId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown user in this organization"));
    }

    private static ExplainAccessResponse response(AccessExplanation explanation) {
        return new ExplainAccessResponse(
                explanation.state(),
                explanation.reasonCode(),
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
                explanation.policyVersion(),
                explanation.evaluatedAt());
    }
}
