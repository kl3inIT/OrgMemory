package com.orgmemory.api.admin;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.authorization.EffectiveAuthorizationService;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
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

    private final CurrentActorProvider actors;
    private final EffectiveAuthorizationService authorization;

    AdminAccessGuard(CurrentActorProvider actors, EffectiveAuthorizationService authorization) {
        this.actors = actors;
        this.authorization = authorization;
    }

    CurrentActor requireAdministrator(Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        boolean allowed = authorization.authorize(
                        actor.organizationId(),
                        actor.principal(),
                        CAN_MANAGE_MEMBERS,
                        ResourceRef.of(actor.organizationId(), "organization", actor.organizationId()))
                .allowed();
        if (!allowed) {
            throw new OrgMemoryAccessDeniedException("The current user cannot administer this organization");
        }
        return actor;
    }
}
