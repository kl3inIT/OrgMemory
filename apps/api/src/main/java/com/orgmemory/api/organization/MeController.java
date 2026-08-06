package com.orgmemory.api.organization;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.organization.Clearance;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.Department;
import com.orgmemory.core.organization.DepartmentRepository;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    private final CurrentActorProvider actors;
    private final DepartmentRepository departments;

    MeController(CurrentActorProvider actors, DepartmentRepository departments) {
        this.actors = actors;
        this.departments = departments;
    }

    record MeResponse(
            UUID userId,
            UUID organizationId,
            String name,
            String email,
            UUID departmentId,
            String departmentName,
            Clearance clearance) {
    }

    @GetMapping("/api/me")
    @Operation(operationId = "getMe", summary = "Read the current user's governed profile")
    @Transactional(readOnly = true)
    MeResponse me(Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        String departmentName = actor.departmentId() == null
                ? null
                : departments.findById(actor.departmentId())
                        .filter(department -> actor.organizationId()
                                .equals(department.getOrganizationId()))
                        .map(Department::getName)
                        .orElse(null);
        return new MeResponse(
                actor.userId(),
                actor.organizationId(),
                actor.name(),
                actor.email(),
                actor.departmentId(),
                departmentName,
                actor.clearance());
    }
}
