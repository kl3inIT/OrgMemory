package com.orgmemory.core.organization;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An address an administrator expects to sign in, and what they become when they do.
 *
 * <p>This is the record that was missing. Authenticating at the identity provider has never been
 * enough to reach OrgMemory — the OIDC subject has to be linked to an app user — and nothing
 * created that link, so users were inserted by hand. An invitation makes the expectation
 * explicit and auditable without widening anything: a sign-in with no matching invitation is
 * still refused.
 *
 * <p>Accepted and revoked rows are kept. Knowing that an address was once invited, by whom, and
 * what became of it is the point of writing it down.
 */
@Entity
@Table(name = "user_invitations")
public class UserInvitation extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, updatable = false)
    private String email;

    @Column(name = "department_id", updatable = false)
    private UUID departmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Clearance clearance;

    @Column(name = "invited_by_user_id", nullable = false, updatable = false)
    private UUID invitedByUserId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "accepted_app_user_id")
    private UUID acceptedAppUserId;

    protected UserInvitation() {
    }

    public UserInvitation(
            UUID organizationId,
            String email,
            UUID departmentId,
            Clearance clearance,
            UUID invitedByUserId) {
        super(UUID.randomUUID());
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.email = normalizeEmail(email);
        this.departmentId = departmentId;
        this.clearance = Objects.requireNonNull(clearance, "clearance");
        this.invitedByUserId = Objects.requireNonNull(invitedByUserId, "invitedByUserId");
    }

    /** Addresses are compared and stored lowercased, because an identity provider may not. */
    public static String normalizeEmail(String value) {
        String normalized = Objects.requireNonNull(value, "email").trim().toLowerCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        return normalized;
    }

    public void accept(UUID appUserId, Instant at) {
        if (!open()) {
            throw new IllegalStateException("Only an open invitation can be accepted");
        }
        this.acceptedAppUserId = Objects.requireNonNull(appUserId, "appUserId");
        this.acceptedAt = Objects.requireNonNull(at, "at");
    }

    public void revoke(Instant at) {
        if (!open()) {
            throw new IllegalStateException("Only an open invitation can be revoked");
        }
        this.revokedAt = Objects.requireNonNull(at, "at");
    }

    public boolean open() {
        return acceptedAt == null && revokedAt == null;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getEmail() {
        return email;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public Clearance getClearance() {
        return clearance;
    }

    public UUID getInvitedByUserId() {
        return invitedByUserId;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public UUID getAcceptedAppUserId() {
        return acceptedAppUserId;
    }
}
