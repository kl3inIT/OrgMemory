package com.orgmemory.core.knowledge.space;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "knowledge_spaces")
class KnowledgeSpace extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "department_id", updatable = false)
    private UUID departmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_mode", nullable = false, length = 32, updatable = false)
    private KnowledgeSpaceAudienceMode audienceMode;

    @Column(name = "audience_version", nullable = false, updatable = false)
    private long audienceVersion;

    @Column(name = "space_key", nullable = false, length = 128, updatable = false)
    private String key;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active;

    protected KnowledgeSpace() {
    }

    KnowledgeSpace(
            UUID organizationId,
            KnowledgeSpaceAudienceMode audienceMode,
            UUID departmentId,
            String key,
            String name) {
        super(UUID.randomUUID());
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.audienceMode = Objects.requireNonNull(audienceMode, "audienceMode");
        this.departmentId = departmentId;
        requireValidAudience(audienceMode, departmentId);
        this.audienceVersion = 1;
        this.key = Objects.requireNonNull(key, "key");
        this.name = Objects.requireNonNull(name, "name");
        this.active = true;
    }

    UUID getDepartmentId() {
        return departmentId;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    KnowledgeSpaceAudienceMode getAudienceMode() {
        return audienceMode;
    }

    long getAudienceVersion() {
        return audienceVersion;
    }

    boolean admits(UUID actorDepartmentId) {
        return audienceMode != KnowledgeSpaceAudienceMode.DEPARTMENT
                || Objects.equals(departmentId, actorDepartmentId);
    }

    String getKey() {
        return key;
    }

    String getName() {
        return name;
    }

    boolean isActive() {
        return active;
    }

    private static void requireValidAudience(
            KnowledgeSpaceAudienceMode audienceMode, UUID departmentId) {
        if (audienceMode == KnowledgeSpaceAudienceMode.DEPARTMENT && departmentId == null) {
            throw new IllegalArgumentException("A department Space requires an owning department");
        }
        if (audienceMode != KnowledgeSpaceAudienceMode.DEPARTMENT && departmentId != null) {
            throw new IllegalArgumentException("Only a department Space may name an owning department");
        }
    }

}
