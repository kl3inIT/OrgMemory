package com.orgmemory.api.admin;

import com.orgmemory.api.ApiRequestException;
import com.orgmemory.core.authorization.RoleAdministrationService;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.shared.error.BusinessUnavailableException;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Granting and revoking the roles OrgMemory owns.
 *
 * <p>This is the whole of what an administrator may change about access. Document permissions
 * arrive from Slack and Drive through convergence and are mirrored, not authored, so there is
 * deliberately no endpoint here that writes one — see {@code AdministrativeTupleScope}.
 */
@RestController
@RequestMapping("/api/admin/roles")
class AdminRoleController {

    private final AdminAccessGuard guard;
    private final RoleAdministrationService roles;

    AdminRoleController(
            AdminAccessGuard guard,
            RoleAdministrationService roles) {
        this.guard = guard;
        this.roles = roles;
    }

    record AdminRoleResponse(String role, List<String> assignees) {
    }

    /** {@code complete} is false when the store was larger than the listing was willing to read. */
    record AdminRoleListResponse(List<AdminRoleResponse> roles, boolean complete, String policyVersion) {
    }

    record AssignRoleRequest(UUID userId) {
    }

    @GetMapping
    @Operation(operationId = "listAdminRoles", summary = "List roles and who is assigned to them")
    @Transactional(readOnly = true)
    AdminRoleListResponse list(Authentication authentication) {
        CurrentActor actor = guard.requireMemberAdministrator(authentication);
        var listing = roles.roles(actor.organizationId());
        return new AdminRoleListResponse(
                listing.roles().stream()
                        .map(role -> new AdminRoleResponse(role.role(), role.assignees()))
                        .toList(),
                listing.complete(),
                listing.policyVersion());
    }

    @PostMapping("/{role}/members")
    @Operation(operationId = "assignAdminRole", summary = "Assign a user to a role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void assign(
            @PathVariable String role,
            @RequestBody AssignRoleRequest request,
            Authentication authentication) {
        CurrentActor actor = guard.requireMemberAdministrator(authentication);
        AppUser user = guard.requireUserInOrganization(
                request.userId(), actor);
        var result = roles.assign(actor.organizationId(), role, user.getId());
        if (!result.applied()) {
            throw new BusinessUnavailableException(
                    "role.assignment-unavailable",
                    "The role assignment could not be applied");
        }
    }

    @DeleteMapping("/{role}/members/{userId}")
    @Operation(operationId = "revokeAdminRole", summary = "Remove a user from a role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void revoke(@PathVariable String role, @PathVariable UUID userId, Authentication authentication) {
        CurrentActor actor = guard.requireMemberAdministrator(authentication);
        // An administrator removing their own last role can lock the organization out of its own
        // administration, and unlike a role change there is nobody left who could undo it.
        if (actor.userId().equals(userId)) {
            throw new ApiRequestException("An administrator cannot revoke their own role");
        }
        AppUser user = guard.requireUserInOrganization(userId, actor);
        var result = roles.revoke(actor.organizationId(), role, user.getId());
        if (!result.applied()) {
            throw new BusinessUnavailableException(
                    "role.revocation-unavailable",
                    "The role revocation could not be applied");
        }
    }
}
