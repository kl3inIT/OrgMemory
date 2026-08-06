package com.orgmemory.core.organization;

import com.orgmemory.core.shared.error.BusinessErrorExposure;
import com.orgmemory.core.shared.error.BusinessConflictException;
import com.orgmemory.core.shared.error.BusinessNotFoundException;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles directory-provisioned and invited users with their first verified
 * sign-in.
 *
 * <p>OrgMemory does not create accounts — the identity provider does — but it has always required
 * an {@code external_identities} row binding the OIDC subject to an app user, and nothing wrote
 * one. That made every onboarding a manual INSERT and made "never invited" indistinguishable from
 * "invited but not linked".
 *
 * <p>This does not open registration. A verified address must select exactly one
 * directory-managed user or exactly one open invitation. The durable binding is
 * still {@code (issuer, subject)} rather than the address.
 */
@Service
public class UserProvisioningService {

    private final AppUserRepository users;
    private final OrganizationRepository organizations;
    private final DepartmentRepository departments;
    private final ExternalIdentityBindingService identityBindings;
    private final UserInvitationRepository invitations;

    public UserProvisioningService(
            AppUserRepository users,
            OrganizationRepository organizations,
            DepartmentRepository departments,
            ExternalIdentityBindingService identityBindings,
            UserInvitationRepository invitations) {
        this.users = Objects.requireNonNull(users, "users");
        this.organizations = Objects.requireNonNull(organizations, "organizations");
        this.departments = Objects.requireNonNull(departments, "departments");
        this.identityBindings = Objects.requireNonNull(identityBindings, "identityBindings");
        this.invitations = Objects.requireNonNull(invitations, "invitations");
    }

    /**
     * Resolves a first verified sign-in. A directory-managed user wins over an
     * invitation for the same address, then the durable issuer/subject binding
     * becomes authoritative for every later sign-in.
     */
    @Transactional
    public Optional<AppUser> provisionForVerifiedSignIn(
            String issuer, String subject, String email) {
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(subject, "subject");
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        Optional<AppUser> alreadyBound = boundActiveUser(issuer, subject);
        if (alreadyBound.isPresent()) {
            return alreadyBound;
        }

        String normalized = UserInvitation.normalizeEmail(email);
        List<AppUser> directoryUsers = users.findByEmailIgnoreCase(normalized).stream()
                .filter(AppUser::isDirectoryManaged)
                .toList();
        if (directoryUsers.size() == 1) {
            AppUser directoryUser = directoryUsers.getFirst();
            if (!directoryUser.isActive()) {
                return Optional.empty();
            }
            identityBindings.bind(directoryUser.getId(), issuer, subject);
            return Optional.of(directoryUser);
        }
        if (!directoryUsers.isEmpty()) {
            return Optional.empty();
        }
        return provisionFromInvitation(issuer, subject, normalized);
    }

    /**
     * Accepts the open invitation for {@code email}, if there is exactly one.
     *
     * <p>Ambiguity fails closed. Two organizations expecting the same address is not a case this
     * can resolve from a sign-in alone, and picking one would silently place somebody in the wrong
     * tenant, so nothing is provisioned and access is refused.
     *
     * <p>An address that already belongs to a user in the invitation's organization is linked
     * rather than duplicated. The organization scope is explicit even while the global email
     * index remains as the H1 rollback-compatibility floor.
     */
    @Transactional
    public Optional<AppUser> provisionFromInvitation(String issuer, String subject, String email) {
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(subject, "subject");
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        Optional<AppUser> alreadyBound = boundActiveUser(issuer, subject);
        if (alreadyBound.isPresent()) {
            return alreadyBound;
        }
        String normalized = UserInvitation.normalizeEmail(email);
        List<UserInvitation> open = invitations.findOpenByEmailForUpdate(normalized);
        if (open.size() != 1) {
            return boundActiveUser(issuer, subject);
        }
        UserInvitation invitation = open.getFirst();

        Optional<AppUser> existing = users.findByOrganizationIdAndEmailIgnoreCase(
                invitation.getOrganizationId(), normalized);
        if (existing.filter(AppUser::isDirectoryManaged).isPresent()) {
            return Optional.empty();
        }
        AppUser user = existing.orElseGet(() -> users.save(new AppUser(
                invitation.getOrganizationId(),
                invitation.getDepartmentId(),
                displayName(normalized),
                normalized,
                invitation.getClearance())));

        identityBindings.bind(user.getId(), issuer, subject);
        invitation.accept(user.getId(), Instant.now());
        invitations.save(invitation);
        return Optional.of(user);
    }

    @Transactional
    public UserInvitation invite(
            UUID organizationId,
            String email,
            UUID departmentId,
            Clearance clearance,
            UUID invitedByUserId) {
        if (organizationId == null || !organizations.existsById(organizationId)) {
            throw new BusinessValidationException(
                    "invitation.organization-invalid",
                    "The invitation organization is not available");
        }
        if (departmentId != null
                && !departments.existsByIdAndOrganizationId(departmentId, organizationId)) {
            throw new BusinessValidationException(
                    "invitation.department-invalid",
                    "The invitation department is not available");
        }
        if (invitedByUserId == null
                || !users.existsByIdAndOrganizationId(invitedByUserId, organizationId)) {
            throw new BusinessValidationException(
                    "invitation.inviter-invalid",
                    "The invitation creator is not available");
        }
        String normalized = UserInvitation.normalizeEmail(email);
        if (users.findByOrganizationIdAndEmailIgnoreCase(organizationId, normalized)
                .filter(AppUser::isDirectoryManaged)
                .isPresent()) {
            throw new BusinessConflictException(
                    "invitation.user-scim-managed",
                    "This user is managed by SCIM and cannot be invited");
        }
        return invitations.save(
                new UserInvitation(
                        organizationId, normalized, departmentId, clearance, invitedByUserId));
    }

    /**
     * Creates or adopts the application actor selected by a tenant-scoped
     * directory request. Email is only the brownfield matching key; the SCIM
     * resource and later issuer/subject binding are the durable identities.
     */
    @Transactional
    public DirectoryProvisioningResult provisionFromDirectory(
            DirectoryUserCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.organizationId() == null
                || !organizations.existsById(command.organizationId())) {
            throw new BusinessValidationException(
                    "directory-user.organization-invalid",
                    "The directory user organization is not available");
        }
        if (command.email() == null || command.email().isBlank()) {
            throw new BusinessValidationException(
                    "directory-user.email-required",
                    "The directory user email is required");
        }
        String normalized = UserInvitation.normalizeEmail(command.email());
        Optional<UserInvitation> openInvitation =
                invitations.findOpenByOrganizationIdAndEmailForUpdate(
                        command.organizationId(), normalized);
        Optional<AppUser> existing = users.findByOrganizationIdAndEmailIgnoreCase(
                command.organizationId(), normalized);

        AppUser user = existing.orElseGet(() -> {
            UUID departmentId = openInvitation
                    .map(UserInvitation::getDepartmentId)
                    .orElse(null);
            Clearance clearance = openInvitation
                    .map(UserInvitation::getClearance)
                    .orElse(Clearance.STANDARD);
            return new AppUser(
                    command.organizationId(),
                    departmentId,
                    preferredDisplayName(command.displayName(), normalized),
                    normalized,
                    clearance);
        });
        user.applyDirectoryProfile(command.displayName());
        user.applyDirectoryAccess(command.directoryActive());
        user = users.save(user);

        UUID consumedInvitationId = null;
        if (openInvitation.isPresent()) {
            UserInvitation invitation = openInvitation.get();
            invitation.accept(user.getId(), Instant.now());
            invitations.save(invitation);
            consumedInvitationId = invitation.getId();
        }
        return new DirectoryProvisioningResult(
                user, existing.isPresent(), consumedInvitationId);
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

    private static String preferredDisplayName(String candidate, String email) {
        return candidate == null || candidate.isBlank()
                ? displayName(email)
                : candidate.trim();
    }

    private Optional<AppUser> boundActiveUser(String issuer, String subject) {
        return identityBindings.findUserId(issuer, subject)
                .flatMap(users::findById)
                .filter(AppUser::isActive);
    }

    public record DirectoryUserCommand(
            UUID organizationId,
            String email,
            String displayName,
            boolean directoryActive) {
    }

    public record DirectoryProvisioningResult(
            AppUser user,
            boolean adoptedExistingUser,
            UUID consumedInvitationId) {
    }
}
