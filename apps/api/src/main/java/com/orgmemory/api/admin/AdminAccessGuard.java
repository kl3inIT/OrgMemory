package com.orgmemory.api.admin;

import com.orgmemory.api.ApiRequestException;
import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.authorization.EffectiveAuthorizationService;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * The boundary for every administration endpoint. The browser separately hides the
 * admin area from non-administrators, but that is a rendering hint: this check is
 * what actually decides, and it is the same OpenFGA decision the rest of the
 * application uses rather than a second notion of who is an admin.
 */
@Component
class AdminAccessGuard {

    private static final PermissionKey CAN_MANAGE_MEMBERS = PermissionKey.of("can_manage_members");
    private static final PermissionKey CAN_MANAGE_SOURCES = PermissionKey.of("can_manage_sources");

    private final CurrentActorProvider actors;
    private final EffectiveAuthorizationService authorization;
    private final AppUserRepository users;

    AdminAccessGuard(
            CurrentActorProvider actors,
            EffectiveAuthorizationService authorization,
            AppUserRepository users) {
        this.actors = actors;
        this.authorization = authorization;
        this.users = users;
    }

    CurrentActor requireMemberAdministrator(Authentication authentication) {
        return require(
                authentication,
                CAN_MANAGE_MEMBERS,
                "The current user cannot administer organization members");
    }

    CurrentActor requireSourceManager(Authentication authentication) {
        return require(
                authentication,
                CAN_MANAGE_SOURCES,
                "The current user cannot administer organization sources");
    }

    AppUser requireUserInOrganization(UUID userId, CurrentActor actor) {
        if (userId == null) {
            throw new ApiRequestException("A user id is required");
        }
        return users.findById(userId)
                .filter(candidate -> candidate.getOrganizationId()
                        .equals(actor.organizationId()))
                .orElseThrow(() -> new ApiRequestException(
                        "Unknown user in this organization"));
    }

    private CurrentActor require(
            Authentication authentication,
            PermissionKey permission,
            String denialMessage) {
        CurrentActor actor = actors.current(authentication);
        boolean allowed = authorization.authorize(
                        actor.organizationId(),
                        actor.principal(),
                        permission,
                        ResourceRef.of(actor.organizationId(), "organization", actor.organizationId()))
                .allowed();
        if (!allowed) {
            throw new OrgMemoryAccessDeniedException(denialMessage);
        }
        return actor;
    }
}
