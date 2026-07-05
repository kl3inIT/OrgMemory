package com.orgmemory.core.organization;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "departments")
public class Department extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    protected Department() {
    }

    public Department(UUID organizationId, String name) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.name = name;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }
}
