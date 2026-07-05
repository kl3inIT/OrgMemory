package com.orgmemory.core.organization;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    protected AppUser() {
    }

    public AppUser(UUID organizationId, UUID departmentId, String name, String email, UserRole role) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.departmentId = departmentId;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public UserRole getRole() {
        return role;
    }
}
