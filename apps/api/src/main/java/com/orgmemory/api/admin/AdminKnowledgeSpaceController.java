package com.orgmemory.api.admin;

import com.orgmemory.api.ApiRequestException;
import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.knowledge.space.KnowledgeSpaceAdministration;
import com.orgmemory.core.knowledge.space.KnowledgeSpaceAdministrationService;
import com.orgmemory.core.knowledge.space.KnowledgeSpaceAudienceMode;
import com.orgmemory.core.knowledge.space.KnowledgeSpaceSubject;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Creating Knowledge Spaces and deciding who reads them.
 *
 * <p>Unlike the rest of this package these endpoints do not go through {@link AdminAccessGuard}.
 * The guard asks {@code can_manage_members}; creating a space is {@code can_create_knowledge_space}
 * and changing its grants is {@code can_manage_acl} on that space. Both resolve to
 * {@code administrator} today, but the model already separates them, and collapsing them here
 * would mean the separation could never be used without rewriting this controller.
 */
@RestController
@RequestMapping("/api/admin/knowledge-spaces")
class AdminKnowledgeSpaceController {

    private final CurrentActorProvider actors;
    private final KnowledgeSpaceAdministrationService spaces;

    AdminKnowledgeSpaceController(
            CurrentActorProvider actors, KnowledgeSpaceAdministrationService spaces) {
        this.actors = actors;
        this.spaces = spaces;
    }

    record CreateKnowledgeSpaceRequest(
            String name, KnowledgeSpaceAudienceMode audienceMode, UUID departmentId) {
    }

    record KnowledgeSpaceGrantResponse(String relation, String subject, boolean effective) {
    }

    /** {@code grantsComplete} is false when the space held more tuples than the listing read. */
    record AdminKnowledgeSpaceResponse(
            UUID id,
            String key,
            String name,
            KnowledgeSpaceAudienceMode audienceMode,
            long audienceVersion,
            UUID departmentId,
            boolean active,
            List<KnowledgeSpaceGrantResponse> grants,
            boolean grantsComplete,
            String policyVersion) {

        static AdminKnowledgeSpaceResponse from(KnowledgeSpaceAdministration.Space space) {
            return new AdminKnowledgeSpaceResponse(
                    space.id(),
                    space.key(),
                    space.name(),
                    space.audienceMode(),
                    space.audienceVersion(),
                    space.departmentId(),
                    space.active(),
                    space.grants().stream()
                            .map(grant -> new KnowledgeSpaceGrantResponse(
                                    grant.relation(), grant.subject(), grant.effective()))
                            .toList(),
                    space.grantsComplete(),
                    space.policyVersion());
        }
    }

    record GrantKnowledgeSpaceAccessRequest(
            String relation, KnowledgeSpaceSubject.Kind kind, UUID subjectId, String role) {
    }

    /** One relation and the subject shapes the authorization model accepts for it. */
    record KnowledgeSpaceGrantOptionResponse(
            String relation, List<KnowledgeSpaceSubject.Kind> kinds, List<String> roles) {
    }

    /**
     * What combinations a grant form may offer.
     *
     * <p>The model does not accept every subject for every relation, so a form built from the
     * cross product offers choices that can only fail. Publishing the table keeps the browser from
     * holding a second copy that drifts from what is enforced.
     */
    @GetMapping("/grant-options")
    @Operation(
            operationId = "listAdminKnowledgeSpaceGrantOptions",
            summary = "List the subject shapes each Knowledge Space relation accepts")
    List<KnowledgeSpaceGrantOptionResponse> grantOptions(Authentication authentication) {
        actors.current(authentication);
        return spaces.grantOptions().entrySet().stream()
                .map(entry -> new KnowledgeSpaceGrantOptionResponse(
                        entry.getKey(),
                        entry.getValue().stream().sorted().toList(),
                        spaces.grantableRoles(entry.getKey()).stream().sorted().toList()))
                .sorted(java.util.Comparator.comparing(KnowledgeSpaceGrantOptionResponse::relation))
                .toList();
    }

    @GetMapping
    @Operation(
            operationId = "listAdminKnowledgeSpaces",
            summary = "List Knowledge Spaces and the grants stored against them")
    List<AdminKnowledgeSpaceResponse> list(Authentication authentication) {
        return spaces.list(actors.current(authentication)).stream()
                .map(AdminKnowledgeSpaceResponse::from)
                .toList();
    }

    @PostMapping
    @Operation(operationId = "createAdminKnowledgeSpace", summary = "Create a Knowledge Space")
    @ResponseStatus(HttpStatus.CREATED)
    AdminKnowledgeSpaceResponse create(
            @RequestBody CreateKnowledgeSpaceRequest request, Authentication authentication) {
        String requestId = UUID.randomUUID().toString();
        return AdminKnowledgeSpaceResponse.from(spaces.create(
                actors.current(authentication),
                request.name(),
                request.audienceMode(),
                request.departmentId(),
                requestId));
    }

    @PostMapping("/{knowledgeSpaceId}/grants")
    @Operation(
            operationId = "grantAdminKnowledgeSpaceAccess",
            summary = "Grant a subject access to a Knowledge Space")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void grant(
            @PathVariable UUID knowledgeSpaceId,
            @RequestBody GrantKnowledgeSpaceAccessRequest request,
            Authentication authentication) {
        spaces.grant(
                actors.current(authentication),
                knowledgeSpaceId,
                request.relation(),
                subject(request.kind(), request.subjectId(), request.role()),
                UUID.randomUUID().toString());
    }

    @DeleteMapping("/{knowledgeSpaceId}/grants")
    @Operation(
            operationId = "revokeAdminKnowledgeSpaceAccess",
            summary = "Revoke a subject's access to a Knowledge Space")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(
            @PathVariable UUID knowledgeSpaceId,
            @RequestParam String relation,
            @RequestParam KnowledgeSpaceSubject.Kind kind,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) String role,
            Authentication authentication) {
        spaces.revoke(
                actors.current(authentication),
                knowledgeSpaceId,
                relation,
                subject(kind, subjectId, role),
                UUID.randomUUID().toString());
    }

    /**
     * A missing field is a request error rather than a null dereference, so each shape names what
     * it needs instead of letting {@code Objects.requireNonNull} answer 500.
     */
    private static KnowledgeSpaceSubject subject(
            KnowledgeSpaceSubject.Kind kind, UUID subjectId, String role) {
        if (kind == null) {
            throw new ApiRequestException("A subject kind is required");
        }
        return switch (kind) {
            case ORGANIZATION -> KnowledgeSpaceSubject.organization();
            case DEPARTMENT -> KnowledgeSpaceSubject.department(require(subjectId, "A department id"));
            case DEPARTMENT_MANAGERS ->
                    KnowledgeSpaceSubject.departmentManagers(require(subjectId, "A department id"));
            case USER -> KnowledgeSpaceSubject.user(require(subjectId, "A user id"));
            case ROLE -> {
                if (role == null || role.isBlank()) {
                    throw new ApiRequestException("A role name is required for a role grant");
                }
                yield KnowledgeSpaceSubject.role(role);
            }
        };
    }

    private static UUID require(UUID value, String field) {
        if (value == null) {
            throw new ApiRequestException(field + " is required for this grant");
        }
        return value;
    }
}
