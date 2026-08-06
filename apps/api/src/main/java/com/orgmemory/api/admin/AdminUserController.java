package com.orgmemory.api.admin;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.orgmemory.api.ApiRequestException;
import com.orgmemory.core.knowledge.acl.SourcePrincipalAdminService;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.Clearance;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.DepartmentRepository;
import com.orgmemory.core.organization.ExternalIdentity;
import com.orgmemory.core.organization.ExternalIdentityRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
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
    private final DepartmentRepository departments;
    private final ExternalIdentityRepository identities;
    private final SourcePrincipalAdminService sourceAdmin;

    AdminUserController(
            AdminAccessGuard guard,
            AppUserRepository users,
            DepartmentRepository departments,
            ExternalIdentityRepository identities,
            SourcePrincipalAdminService sourceAdmin) {
        this.guard = guard;
        this.users = users;
        this.departments = departments;
        this.identities = identities;
        this.sourceAdmin = sourceAdmin;
    }

    record AdminUserResponse(
            UUID id,
            String name,
            String email,
            Clearance clearance,
            UUID departmentId,
            boolean active,
            boolean signInLinked,
            int mappedPrincipalCount) {
    }

    static final class UpdateAdminUserRequest {

        private Clearance clearance;
        private Boolean active;
        private UUID departmentId;
        private boolean departmentIdPresent;

        public Clearance getClearance() {
            return clearance;
        }

        @JsonSetter
        public void setClearance(Clearance clearance) {
            this.clearance = clearance;
        }

        public Boolean getActive() {
            return active;
        }

        @JsonSetter
        public void setActive(Boolean active) {
            this.active = active;
        }

        @Schema(
                nullable = true,
                description = "Omit to keep the current department; send null to clear it")
        public UUID getDepartmentId() {
            return departmentId;
        }

        @JsonSetter
        public void setDepartmentId(UUID departmentId) {
            this.departmentIdPresent = true;
            this.departmentId = departmentId;
        }

        boolean departmentIdPresent() {
            return departmentIdPresent;
        }
    }

    @GetMapping
    @Operation(operationId = "listAdminUsers", summary = "List internal users with their sign-in and mapping status")
    @Transactional(readOnly = true)
    List<AdminUserResponse> list(Authentication authentication) {
        CurrentActor actor = guard.requireMemberAdministrator(authentication);
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
                        user.getClearance(),
                        user.getDepartmentId(),
                        user.isActive(),
                        linked.contains(user.getId()),
                        mapped.getOrDefault(user.getId(), 0)))
                .toList();
    }

    @PatchMapping("/{userId}")
    @Operation(
            operationId = "updateAdminUser",
            summary = "Change a user's clearance, department, or activation")
    @Transactional
    AdminUserResponse update(
            @PathVariable UUID userId,
            @RequestBody UpdateAdminUserRequest request,
            Authentication authentication) {
        CurrentActor actor = guard.requireMemberAdministrator(authentication);
        // Self-edits are the one way an administrator can lock the organization out of
        // its own administration surface, so they are refused rather than confirmed.
        if (actor.userId().equals(userId)) {
            throw new ApiRequestException(
                    "An administrator cannot change their own clearance, department, or activation");
        }
        AppUser user = guard.requireUserInOrganization(userId, actor);

        if (request.getClearance() != null) {
            user.changeClearance(request.getClearance());
        }
        if (request.departmentIdPresent()) {
            UUID departmentId = request.getDepartmentId();
            if (departmentId != null
                    && !departments.existsByIdAndOrganizationId(
                            departmentId, actor.organizationId())) {
                throw new ApiRequestException(
                        "The department is not available in this organization");
            }
            user.changeDepartment(departmentId);
        }
        if (request.getActive() != null) {
            if (request.getActive()) {
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
                user.getClearance(),
                user.getDepartmentId(),
                user.isActive(),
                signInLinked,
                sourceAdmin.mappedPrincipalCountByUser(actor.organizationId()).getOrDefault(user.getId(), 0));
    }
}
