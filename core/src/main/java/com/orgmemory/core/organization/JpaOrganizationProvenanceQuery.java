package com.orgmemory.core.organization;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class JpaOrganizationProvenanceQuery implements OrganizationProvenanceQuery {

    private final DepartmentRepository departments;
    private final AppUserRepository users;

    JpaOrganizationProvenanceQuery(
            DepartmentRepository departments,
            AppUserRepository users) {
        this.departments = departments;
        this.users = users;
    }

    @Override
    public Map<UUID, String> departmentNames(
            UUID organizationId, Collection<UUID> departmentIds) {
        if (departmentIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> result = new LinkedHashMap<>();
        departments.findByOrganizationIdAndIdIn(organizationId, departmentIds)
                .forEach(department -> result.put(department.getId(), department.getName()));
        return Map.copyOf(result);
    }

    @Override
    public Map<UUID, String> userNames(
            UUID organizationId, Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> result = new LinkedHashMap<>();
        users.findByOrganizationIdAndIdIn(organizationId, userIds)
                .forEach(user -> result.put(user.getId(), user.getName()));
        return Map.copyOf(result);
    }
}
