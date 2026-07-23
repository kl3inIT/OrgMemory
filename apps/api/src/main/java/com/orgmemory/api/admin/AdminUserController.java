package com.orgmemory.api.admin;

import com.orgmemory.core.knowledge.SourcePrincipalAdminService;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.ExternalIdentity;
import com.orgmemory.core.organization.ExternalIdentityRepository;
import com.orgmemory.core.organization.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration of internal users. Accounts are created in the identity provider,
 * not here: this surface governs what an existing user may do and whether they are
 * still active, and reports whether they are linked well enough to sign in at all.
 */
@RestController
@RequestMapping("/api/admin/users")
class AdminUserController {

    private final AdminAccessGuard guard;
    private final AppUserRepository users;
    private final ExternalIdentityRepository identities;
    private final SourcePrincipalAdminService sourceAdmin;

    AdminUserController(
            AdminAccessGuard guard,
            AppUserRepository users,
            ExternalIdentityRepository identities,
            SourcePrincipalAdminService sourceAdmin) {
        this.guard = guard;
        this.users = users;
        this.identities = identities;
        this.sourceAdmin = sourceAdmin;
    }

    record AdminUserResponse(
            UUID id,
            String name,
            String email,
            UserRole role,
            UUID departmentId,
            boolean active,
            boolean signInLinked,
            int mappedPrincipalCount) {
    }

    record UpdateAdminUserRequest(UserRole role, Boolean active) {
    }

    @GetMapping
    @Operation(operationId = "listAdminUsers", summary = "List internal users with their sign-in and mapping status")
    @Transactional(readOnly = true)
    List<AdminUserResponse> list(Authentication authentication) {
        CurrentActor actor = guard.requireAdministrator(authentication);
        List<AppUser> organizationUsers = users.findByOrganizationIdOrderByName(actor.organizationId());
        Set<UUID> linked = identities
                .findByAppUserIdIn(organizationUsers.stream().map(AppUser::getId).toList())
                .stream()
                .map(ExternalIdentity::getAppUserId)
                .collect(Collectors.toSet());
        Map<UUID, Integer> mapped = sourceAdmin.mappedPrincipalCountByUser(actor.organizationId());

        return organizationUsers.stream()
                .map(user -> new AdminUserResponse(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getRole(),
                        user.getDepartmentId(),
                        user.isActive(),
                        linked.contains(user.getId()),
                        mapped.getOrDefault(user.getId(), 0)))
                .toList();
    }

    @PatchMapping("/{userId}")
    @Operation(operationId = "updateAdminUser", summary = "Change a user's role or activation")
    @Transactional
    AdminUserResponse update(
            @PathVariable UUID userId,
            @RequestBody UpdateAdminUserRequest request,
            Authentication authentication) {
        CurrentActor actor = guard.requireAdministrator(authentication);
        // Self-edits are the one way an administrator can lock the organization out of
        // its own administration surface, so they are refused rather than confirmed.
        if (actor.userId().equals(userId)) {
            throw new IllegalArgumentException("An administrator cannot change their own role or activation");
        }
        AppUser user = users.findById(userId)
                .filter(candidate -> candidate.getOrganizationId().equals(actor.organizationId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown user in this organization"));

        if (request.role() != null) {
            user.changeRole(request.role());
        }
        if (request.active() != null) {
            if (request.active()) {
                user.activate();
            } else {
                user.deactivate();
            }
        }
        users.save(user);

        boolean signInLinked = !identities.findByAppUserIdIn(List.of(user.getId())).isEmpty();
        return new AdminUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getDepartmentId(),
                user.isActive(),
                signInLinked,
                sourceAdmin.mappedPrincipalCountByUser(actor.organizationId()).getOrDefault(user.getId(), 0));
    }
}
