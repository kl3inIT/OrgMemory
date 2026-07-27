package com.orgmemory.core.identityprovisioning;

import com.orgmemory.core.organization.UserProvisioningService;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-scoped application boundary for the provisioning ledger. Protocol and
 * HTTP concerns deliberately live outside this module.
 */
@Service
public class ProvisioningLedgerService {

    private final ProvisioningConnectionRepository connections;
    private final ProvisioningCredentialRepository credentials;
    private final ScimUserResourceRepository users;
    private final ProvisioningEventRepository events;
    private final UserProvisioningService userProvisioning;
    private final Clock clock = Clock.systemUTC();

    public ProvisioningLedgerService(
            ProvisioningConnectionRepository connections,
            ProvisioningCredentialRepository credentials,
            ScimUserResourceRepository users,
            ProvisioningEventRepository events,
            UserProvisioningService userProvisioning) {
        this.connections = connections;
        this.credentials = credentials;
        this.users = users;
        this.events = events;
        this.userProvisioning = userProvisioning;
    }

    @Transactional
    public ConnectionView createDisabledConnection(
            UUID organizationId,
            String alias,
            ProvisioningProviderProfile providerProfile,
            boolean usersEnabled,
            boolean groupsEnabled) {
        ProvisioningConnection connection = connections.save(new ProvisioningConnection(
                organizationId, alias, providerProfile, usersEnabled, groupsEnabled));
        return ConnectionView.from(connection);
    }

    @Transactional(readOnly = true)
    public ConnectionView requireConnection(UUID organizationId, UUID connectionId) {
        return ConnectionView.from(requireConnectionEntity(organizationId, connectionId));
    }

    @Transactional(readOnly = true)
    public List<ConnectionView> listConnections(UUID organizationId) {
        return connections.findByOrganizationIdOrderByAlias(organizationId).stream()
                .map(ConnectionView::from)
                .toList();
    }

    /**
     * Optimistic compare-and-set. PostgreSQL owns both the version check and the
     * one-active-connection invariant, so competing nodes cannot both win.
     */
    @Transactional
    public boolean compareAndSetOperationalState(
            UUID organizationId,
            UUID connectionId,
            long expectedVersion,
            ProvisioningOperationalState expectedState,
            ProvisioningOperationalState nextState) {
        Objects.requireNonNull(expectedState, "expectedState");
        Objects.requireNonNull(nextState, "nextState");
        return connections.compareAndSetOperationalState(
                        organizationId,
                        connectionId,
                        expectedVersion,
                        expectedState.name(),
                        nextState.name(),
                        clock.instant())
                == 1;
    }

    @Transactional
    public UUID storeCredentialVerifier(CredentialVerifierCommand command) {
        Objects.requireNonNull(command, "command");
        requireConnectionEntity(command.organizationId(), command.connectionId());
        ProvisioningCredential credential = credentials.save(new ProvisioningCredential(
                command.organizationId(),
                command.connectionId(),
                command.publicTokenId(),
                command.verifierDigest(),
                command.verifierKeyVersion(),
                command.usersScope(),
                command.groupsScope(),
                command.expiresAt(),
                command.createdByUserId()));
        return credential.getId();
    }

    @Transactional(readOnly = true)
    public List<CredentialView> listCredentials(UUID organizationId, UUID connectionId) {
        requireConnectionEntity(organizationId, connectionId);
        return credentials
                .findByOrganizationIdAndConnectionIdOrderByCreatedAtDesc(
                        organizationId, connectionId)
                .stream()
                .map(CredentialView::from)
                .toList();
    }

    /**
     * The public lookup ID is globally unique by database constraint. This is
     * the sole unscoped lookup and is exposed only to the machine-auth boundary;
     * failures there remain deliberately indistinguishable.
     */
    @Transactional(readOnly = true)
    public CredentialAuthentication credentialForAuthentication(String publicTokenId) {
        ProvisioningCredential credential = credentials.findByPublicTokenId(publicTokenId)
                .orElseThrow(() -> new ProvisioningNotFoundException(
                        "Credential was not found"));
        ProvisioningConnection connection = requireConnectionEntity(
                credential.getOrganizationId(), credential.getConnectionId());
        return CredentialAuthentication.from(
                credential.authenticationView(connection.getOperationalState()));
    }

    @Transactional
    public void markCredentialUsed(
            UUID organizationId, UUID credentialId, Instant usedAt) {
        credentials.markUsed(credentialId, organizationId, usedAt);
    }

    @Transactional
    public UUID rotateCredential(
            UUID organizationId,
            UUID connectionId,
            UUID oldCredentialId,
            Instant overlapEndsAt,
            CredentialVerifierCommand replacement) {
        if (!organizationId.equals(replacement.organizationId())
                || !connectionId.equals(replacement.connectionId())) {
            throw new IllegalArgumentException("Replacement credential ownership must match");
        }
        ProvisioningCredential old = requireCredentialForUpdate(
                organizationId, connectionId, oldCredentialId);
        old.beginOverlap(overlapEndsAt);
        credentials.save(old);
        return storeCredentialVerifier(replacement);
    }

    @Transactional
    public void revokeCredential(
            UUID organizationId,
            UUID connectionId,
            UUID credentialId,
            UUID revokedByUserId) {
        ProvisioningCredential credential = requireCredentialForUpdate(
                organizationId, connectionId, credentialId);
        credential.revoke(revokedByUserId, clock.instant());
        credentials.save(credential);
    }

    @Transactional
    public UserResourceRegistration registerUserResource(UserResourceCommand command) {
        Objects.requireNonNull(command, "command");
        requireConnectionEntity(command.organizationId(), command.connectionId());
        ensureNewUserResource(command);
        var actor = userProvisioning.provisionFromDirectory(
                new UserProvisioningService.DirectoryUserCommand(
                        command.organizationId(),
                        command.email(),
                        command.displayName(),
                        command.directoryActive()));
        if (users.existsByOrganizationIdAndAppUserId(
                command.organizationId(), actor.user().getId())) {
            throw new ProvisioningConflictException(
                    "The application user is already managed by SCIM");
        }
        ScimUserResource resource;
        try {
            resource = users.saveAndFlush(new ScimUserResource(
                    command.organizationId(),
                    command.connectionId(),
                    actor.user().getId(),
                    command.externalId(),
                    command.userName(),
                    command.email(),
                    command.workforceKey(),
                    command.displayName(),
                    command.givenName(),
                    command.familyName(),
                    command.directoryActive()));
        } catch (DataIntegrityViolationException concurrentConflict) {
            throw new ProvisioningConflictException(
                    "The application user is already managed by SCIM",
                    concurrentConflict);
        }
        return new UserResourceRegistration(
                resource.getId(),
                actor.user().getId(),
                actor.adoptedExistingUser(),
                actor.consumedInvitationId());
    }

    @Transactional
    public UUID appendEvent(EventCommand command) {
        Objects.requireNonNull(command, "command");
        requireConnectionEntity(command.organizationId(), command.connectionId());
        ProvisioningEvent event = events.save(new ProvisioningEvent(
                command.organizationId(),
                command.connectionId(),
                command.resourceId(),
                command.publicTokenId(),
                command.requestId(),
                command.operation(),
                command.outcome(),
                command.reasonCode(),
                command.changedFields(),
                clock.instant()));
        return event.getId();
    }

    private ProvisioningConnection requireConnectionEntity(
            UUID organizationId, UUID connectionId) {
        return connections.findByIdAndOrganizationId(connectionId, organizationId)
                .orElseThrow(() -> new ProvisioningNotFoundException(
                        "Provisioning connection was not found"));
    }

    private ProvisioningCredential requireCredentialForUpdate(
            UUID organizationId, UUID connectionId, UUID credentialId) {
        return credentials
                .findForUpdate(credentialId, organizationId, connectionId)
                .orElseThrow(() -> new ProvisioningNotFoundException(
                        "Provisioning credential was not found"));
    }

    private void ensureNewUserResource(UserResourceCommand command) {
        String externalId = optionalText(command.externalId());
        if (externalId != null
                && users.findByOrganizationIdAndConnectionIdAndExternalId(
                                command.organizationId(),
                                command.connectionId(),
                                externalId)
                        .isPresent()) {
            throw new ProvisioningConflictException(
                    "A SCIM user with this externalId already exists");
        }
        String normalizedUserName = requiredNormalized(
                command.userName(), "userName");
        if (users.findByOrganizationIdAndConnectionIdAndNormalizedUserName(
                        command.organizationId(),
                        command.connectionId(),
                        normalizedUserName)
                .isPresent()) {
            throw new ProvisioningConflictException(
                    "A SCIM user with this userName already exists");
        }
    }

    private static String requiredNormalized(String value, String field) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized.toLowerCase(java.util.Locale.ROOT);
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ConnectionView(
            UUID id,
            UUID organizationId,
            String alias,
            ProvisioningProviderProfile providerProfile,
            ProvisioningConfigurationStatus configurationStatus,
            ProvisioningOperationalState operationalState,
            boolean usersEnabled,
            boolean groupsEnabled,
            long version) {

        private static ConnectionView from(ProvisioningConnection connection) {
            return new ConnectionView(
                    connection.getId(),
                    connection.getOrganizationId(),
                    connection.getAlias(),
                    connection.getProviderProfile(),
                    connection.getConfigurationStatus(),
                    connection.getOperationalState(),
                    connection.isUsersEnabled(),
                    connection.isGroupsEnabled(),
                    connection.getVersion());
        }
    }

    public record CredentialVerifierCommand(
            UUID organizationId,
            UUID connectionId,
            String publicTokenId,
            String verifierDigest,
            int verifierKeyVersion,
            boolean usersScope,
            boolean groupsScope,
            Instant expiresAt,
            UUID createdByUserId) {
    }

    public record CredentialView(
            UUID id,
            String publicTokenId,
            int verifierKeyVersion,
            boolean usersScope,
            boolean groupsScope,
            Instant expiresAt,
            Instant overlapEndsAt,
            Instant revokedAt,
            Instant lastUsedAt,
            Instant createdAt) {

        private static CredentialView from(ProvisioningCredential credential) {
            var view = credential.view();
            return new CredentialView(
                    view.id(),
                    view.publicTokenId(),
                    view.verifierKeyVersion(),
                    view.usersScope(),
                    view.groupsScope(),
                    view.expiresAt(),
                    view.overlapEndsAt(),
                    view.revokedAt(),
                    view.lastUsedAt(),
                    view.createdAt());
        }
    }

    public record CredentialAuthentication(
            UUID credentialId,
            UUID organizationId,
            UUID connectionId,
            String publicTokenId,
            String verifierDigest,
            int verifierKeyVersion,
            boolean usersScope,
            boolean groupsScope,
            Instant expiresAt,
            Instant overlapEndsAt,
            Instant revokedAt,
            Instant lastUsedAt,
            ProvisioningOperationalState connectionState) {

        private static CredentialAuthentication from(
                ProvisioningCredential.CredentialAuthenticationView view) {
            return new CredentialAuthentication(
                    view.credentialId(),
                    view.organizationId(),
                    view.connectionId(),
                    view.publicTokenId(),
                    view.verifierDigest(),
                    view.verifierKeyVersion(),
                    view.usersScope(),
                    view.groupsScope(),
                    view.expiresAt(),
                    view.overlapEndsAt(),
                    view.revokedAt(),
                    view.lastUsedAt(),
                    view.connectionState());
        }
    }

    public record UserResourceCommand(
            UUID organizationId,
            UUID connectionId,
            String externalId,
            String userName,
            String email,
            String workforceKey,
            String displayName,
            String givenName,
            String familyName,
            boolean directoryActive) {
    }

    public record UserResourceRegistration(
            UUID resourceId,
            UUID appUserId,
            boolean adoptedExistingUser,
            UUID consumedInvitationId) {
    }

    public record EventCommand(
            UUID organizationId,
            UUID connectionId,
            UUID resourceId,
            String publicTokenId,
            String requestId,
            ProvisioningEventOperation operation,
            ProvisioningEventOutcome outcome,
            String reasonCode,
            Collection<String> changedFields) {
    }
}
