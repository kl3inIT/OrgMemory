package com.orgmemory.core.organization;

import com.orgmemory.core.shared.error.BusinessErrorExposure;
import com.orgmemory.core.shared.error.BusinessNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns an expected address into a user, the first time that address signs in.
 *
 * <p>OrgMemory does not create accounts — the identity provider does — but it has always required
 * an {@code external_identities} row binding the OIDC subject to an app user, and nothing wrote
 * one. That made every onboarding a manual INSERT and made "never invited" indistinguishable from
 * "invited but not linked".
 *
 * <p>This does not widen access. A sign-in with no open invitation is refused exactly as before;
 * what changes is that an administrator can record the expectation in advance, and the binding is
 * still to {@code (issuer, subject)} rather than to the address. The address only chooses which
 * invitation applies; it never becomes the identity.
 */
@Service
public class UserProvisioningService {

    private final AppUserRepository users;
    private final ExternalIdentityRepository identities;
    private final UserInvitationRepository invitations;

    public UserProvisioningService(
            AppUserRepository users,
            ExternalIdentityRepository identities,
            UserInvitationRepository invitations) {
        this.users = Objects.requireNonNull(users, "users");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.invitations = Objects.requireNonNull(invitations, "invitations");
    }

    /**
     * Accepts the open invitation for {@code email}, if there is exactly one.
     *
     * <p>Ambiguity fails closed. Two organizations expecting the same address is not a case this
     * can resolve from a sign-in alone, and picking one would silently place somebody in the wrong
     * tenant, so nothing is provisioned and access is refused.
     *
     * <p>An address that already belongs to a user is linked rather than duplicated: the
     * unique index on {@code lower(email)} would refuse the insert anyway, and someone whose
     * account predates this mechanism still needs the binding an invitation would have created.
     */
    @Transactional
    public Optional<AppUser> provisionFromInvitation(String issuer, String subject, String email) {
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(subject, "subject");
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String normalized = UserInvitation.normalizeEmail(email);
        List<UserInvitation> open = invitations.findOpenByEmail(normalized);
        if (open.size() != 1) {
            return Optional.empty();
        }
        UserInvitation invitation = open.getFirst();

        AppUser user = users.findByEmailIgnoreCase(normalized)
                .orElseGet(() -> users.save(new AppUser(
                        invitation.getOrganizationId(),
                        invitation.getDepartmentId(),
                        displayName(normalized),
                        normalized,
                        invitation.getRole())));

        identities.linkIfAbsent(UUID.randomUUID(), user.getId(), issuer, subject);
        invitation.accept(user.getId(), Instant.now());
        invitations.save(invitation);
        return Optional.of(user);
    }

    @Transactional
    public UserInvitation invite(
            UUID organizationId, String email, UUID departmentId, UserRole role, UUID invitedByUserId) {
        return invitations.save(
                new UserInvitation(organizationId, email, departmentId, role, invitedByUserId));
    }

    @Transactional
    public void revoke(UUID organizationId, UUID invitationId) {
        UserInvitation invitation = invitations.findByIdAndOrganizationId(invitationId, organizationId)
                .orElseThrow(() -> new BusinessNotFoundException(
                        "invitation.not-found",
                        "The invitation is not available",
                        BusinessErrorExposure.OPAQUE_RESOURCE));
        invitation.revoke(Instant.now());
        invitations.save(invitation);
    }

    @Transactional(readOnly = true)
    public List<UserInvitation> forOrganization(UUID organizationId) {
        return invitations.findByOrganizationIdOrderByEmail(organizationId);
    }

    /** A placeholder until the person or an administrator supplies a real one. */
    private static String displayName(String email) {
        int at = email.indexOf('@');
        return at <= 0 ? email : email.substring(0, at);
    }
}
