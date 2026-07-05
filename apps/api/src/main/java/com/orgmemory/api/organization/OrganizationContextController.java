package com.orgmemory.api.organization;

import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.DepartmentRepository;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organization")
class OrganizationContextController {

    private static final UUID DEFAULT_ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final DepartmentRepository departmentRepository;
    private final AppUserRepository userRepository;

    OrganizationContextController(DepartmentRepository departmentRepository, AppUserRepository userRepository) {
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/context")
    OrganizationContextResponse context(@RequestParam(required = false) UUID organizationId) {
        UUID resolvedOrganizationId = organizationId == null ? DEFAULT_ORGANIZATION_ID : organizationId;
        return new OrganizationContextResponse(
                resolvedOrganizationId,
                departmentRepository.findByOrganizationIdOrderByName(resolvedOrganizationId)
                        .stream()
                        .map(DepartmentResponse::from)
                        .toList(),
                userRepository.findByOrganizationIdOrderByName(resolvedOrganizationId)
                        .stream()
                        .map(UserResponse::from)
                        .toList()
        );
    }
}
