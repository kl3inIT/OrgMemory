package com.orgmemory.core.knowledge.space;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/** PostgreSQL authority for one explicit restricted-custom viewer audience entry. */
@Entity
@Table(name = "knowledge_space_custom_viewer_grants")
class KnowledgeSpaceCustomViewerGrant extends BaseEntity {

    enum SubjectKind {
        USER,
        DEPARTMENT
    }

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "knowledge_space_id", nullable = false, updatable = false)
    private UUID knowledgeSpaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_kind", nullable = false, length = 32, updatable = false)
    private SubjectKind subjectKind;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "department_id", updatable = false)
    private UUID departmentId;

    protected KnowledgeSpaceCustomViewerGrant() {
    }

    private KnowledgeSpaceCustomViewerGrant(
            UUID organizationId,
            UUID knowledgeSpaceId,
            SubjectKind subjectKind,
            UUID userId,
            UUID departmentId) {
        super(UUID.randomUUID());
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.knowledgeSpaceId = Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
        this.subjectKind = Objects.requireNonNull(subjectKind, "subjectKind");
        this.userId = userId;
        this.departmentId = departmentId;
    }

    static KnowledgeSpaceCustomViewerGrant user(
            UUID organizationId, UUID knowledgeSpaceId, UUID userId) {
        return new KnowledgeSpaceCustomViewerGrant(
                organizationId, knowledgeSpaceId, SubjectKind.USER, userId, null);
    }

    static KnowledgeSpaceCustomViewerGrant department(
            UUID organizationId, UUID knowledgeSpaceId, UUID departmentId) {
        return new KnowledgeSpaceCustomViewerGrant(
                organizationId, knowledgeSpaceId, SubjectKind.DEPARTMENT, null, departmentId);
    }
}
