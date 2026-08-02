package com.orgmemory.core.knowledge.space;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface KnowledgeSpaceCustomViewerGrantRepository
        extends JpaRepository<KnowledgeSpaceCustomViewerGrant, UUID> {

    Optional<KnowledgeSpaceCustomViewerGrant>
            findByOrganizationIdAndKnowledgeSpaceIdAndSubjectKindAndUserId(
                    UUID organizationId,
                    UUID knowledgeSpaceId,
                    KnowledgeSpaceCustomViewerGrant.SubjectKind subjectKind,
                    UUID userId);

    Optional<KnowledgeSpaceCustomViewerGrant>
            findByOrganizationIdAndKnowledgeSpaceIdAndSubjectKindAndDepartmentId(
                    UUID organizationId,
                    UUID knowledgeSpaceId,
                    KnowledgeSpaceCustomViewerGrant.SubjectKind subjectKind,
                    UUID departmentId);
}
