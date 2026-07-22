package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "knowledge_spaces")
class KnowledgeSpace extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "department_id", updatable = false)
    private UUID departmentId;

    @Column(name = "space_key", nullable = false, length = 128, updatable = false)
    private String key;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active;

    protected KnowledgeSpace() {
    }

    UUID getDepartmentId() {
        return departmentId;
    }

    String getKey() {
        return key;
    }

    String getName() {
        return name;
    }

}
