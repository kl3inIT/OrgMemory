package com.orgmemory.api.organization;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.authorization.EffectiveAuthorizationService;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.DepartmentRepository;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organization")
class OrganizationContextController {

    private static final PermissionKey CAN_VIEW_DIRECTORY = PermissionKey.of("can_view_directory");

    private final DepartmentRepository departmentRepository;
    private final AppUserRepository userRepository;
    private final CurrentActorProvider actors;
    private final EffectiveAuthorizationService authorization;

    OrganizationContextController(DepartmentRepository departmentRepository, AppUserRepository userRepository,
            CurrentActorProvider actors, EffectiveAuthorizationService authorization) {
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.actors = actors;
        this.authorization = authorization;
    }

    @GetMapping("/context")
    OrganizationContextResponse context(Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        if (!authorization.authorize(
                actor.organizationId(),
                actor.principal(),
                CAN_VIEW_DIRECTORY,
                ResourceRef.of(actor.organizationId(), "organization", actor.organizationId())).allowed()) {
            throw new OrgMemoryAccessDeniedException("The current user cannot view the organization directory");
        }
        return new OrganizationContextResponse(
                actor.organizationId(),
                departmentRepository.findByOrganizationIdOrderByName(actor.organizationId())
                        .stream()
                        .map(DepartmentResponse::from)
                        .toList(),
                userRepository.findByOrganizationIdOrderByName(actor.organizationId())
                        .stream()
                        .map(UserResponse::from)
                        .toList()
        );
    }
}
