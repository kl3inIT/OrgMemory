package com.orgmemory.core.organization;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class JpaKnowledgeAccessSubjectQuery implements KnowledgeAccessSubjectQuery {

    private final AppUserRepository users;

    JpaKnowledgeAccessSubjectQuery(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public Optional<KnowledgeAccessSubject> findActive(
            UUID organizationId,
            UUID userId) {
        UUID tenantId = Objects.requireNonNull(organizationId, "organizationId");
        UUID subjectId = Objects.requireNonNull(userId, "userId");
        return users.findById(subjectId)
                .filter(candidate -> tenantId.equals(candidate.getOrganizationId()))
                .filter(AppUser::isActive)
                .map(candidate -> new KnowledgeAccessSubject(
                        subjectId,
                        tenantId,
                        candidate.getDepartmentId(),
                        candidate.getRole() == UserRole.EXECUTIVE));
    }
}
