package com.orgmemory.api.security;

import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.UserRole;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    private final CurrentActorProvider actors;
    private final AppUserRepository users;

    MeController(CurrentActorProvider actors, AppUserRepository users) {
        this.actors = actors;
        this.users = users;
    }

    record MeResponse(
            boolean authenticated,
            String subject,
            String email,
            String name,
            String authorizationProvider,
            UUID userId,
            UUID organizationId,
            UUID departmentId,
            UserRole role) {
    }

    @GetMapping("/api/me")
    MeResponse me(Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        return new MeResponse(
                true,
                authentication.getName(),
                actor.email(),
                actor.name(),
                "openfga",
                actor.userId(),
                actor.organizationId(),
                actor.departmentId(),
                users.findById(actor.userId()).map(AppUser::getRole).orElse(null));
    }
}
