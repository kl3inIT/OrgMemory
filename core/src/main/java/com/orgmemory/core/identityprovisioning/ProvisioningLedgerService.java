package com.orgmemory.core.identityprovisioning;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
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
    private final Clock clock = Clock.systemUTC();

    public ProvisioningLedgerService(
            ProvisioningConnectionRepository connections,
            ProvisioningCredentialRepository credentials,
            ScimUserResourceRepository users,
            ProvisioningEventRepository events) {
        this.connections = connections;
        this.credentials = credentials;
        this.users = users;
        this.events = events;
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

    @Transactional
    public UUID registerUserResource(UserResourceCommand command) {
        Objects.requireNonNull(command, "command");
        requireConnectionEntity(command.organizationId(), command.connectionId());
        ScimUserResource resource = users.save(new ScimUserResource(
                command.organizationId(),
                command.connectionId(),
                command.appUserId(),
                command.externalId(),
                command.userName(),
                command.email(),
                command.workforceKey(),
                command.displayName(),
                command.givenName(),
                command.familyName(),
                command.directoryActive()));
        return resource.getId();
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

    public record UserResourceCommand(
            UUID organizationId,
            UUID connectionId,
            UUID appUserId,
            String externalId,
            String userName,
            String email,
            String workforceKey,
            String displayName,
            String givenName,
            String familyName,
            boolean directoryActive) {
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
