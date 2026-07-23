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

    @Column(nullable = false)
    private boolean active;

    protected AppUser() {
    }

    public AppUser(UUID organizationId, UUID departmentId, String name, String email, UserRole role) {
        this(organizationId, departmentId, name, email, role, true);
    }

    public AppUser(
            UUID organizationId,
            UUID departmentId,
            String name,
            String email,
            UserRole role,
            boolean active) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.departmentId = departmentId;
        this.name = name;
        this.email = email;
        this.role = role;
        this.active = active;
    }

    public void changeRole(UserRole role) {
        if (role == null) {
            throw new IllegalArgumentException("A user role is required");
        }
        this.role = role;
    }

    /**
     * Deactivation is the only revocation this application controls. The account
     * still exists in the identity provider; an inactive user fails the identity
     * boundary on the next request and resolves no source principal mapping.
     */
    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
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

    public boolean isActive() {
        return active;
    }
}
