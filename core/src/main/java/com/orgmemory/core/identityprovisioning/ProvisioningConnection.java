package com.orgmemory.core.identityprovisioning;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "provisioning_connections")
class ProvisioningConnection extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 128, updatable = false)
    private String alias;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_profile", nullable = false, length = 32)
    private ProvisioningProviderProfile providerProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "configuration_status", nullable = false, length = 32)
    private ProvisioningConfigurationStatus configurationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_state", nullable = false, length = 32)
    private ProvisioningOperationalState operationalState;

    @Column(name = "users_enabled", nullable = false)
    private boolean usersEnabled;

    @Column(name = "groups_enabled", nullable = false)
    private boolean groupsEnabled;

    @Column(name = "keycloak_realm", length = 128)
    private String keycloakRealm;

    @Column(name = "keycloak_client_id")
    private String keycloakClientId;

    @Column(name = "keycloak_idp_alias", length = 128)
    private String keycloakIdpAlias;

    @Column(name = "mapper_fingerprint", length = 128)
    private String mapperFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "correlation_probe_status", nullable = false, length = 32)
    private CorrelationProbeStatus correlationProbeStatus;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "enabled_at")
    private Instant enabledAt;

    protected ProvisioningConnection() {
    }

    ProvisioningConnection(
            UUID organizationId,
            String alias,
            ProvisioningProviderProfile providerProfile,
            boolean usersEnabled,
            boolean groupsEnabled) {
        super(UUID.randomUUID());
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.alias = requireText(alias, "alias");
        this.providerProfile = Objects.requireNonNull(providerProfile, "providerProfile");
        this.configurationStatus = ProvisioningConfigurationStatus.DRAFT;
        this.operationalState = ProvisioningOperationalState.DISABLED;
        this.usersEnabled = usersEnabled;
        this.groupsEnabled = groupsEnabled;
        this.correlationProbeStatus = CorrelationProbeStatus.NOT_RUN;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    String getAlias() {
        return alias;
    }

    ProvisioningProviderProfile getProviderProfile() {
        return providerProfile;
    }

    ProvisioningConfigurationStatus getConfigurationStatus() {
        return configurationStatus;
    }

    ProvisioningOperationalState getOperationalState() {
        return operationalState;
    }

    boolean isUsersEnabled() {
        return usersEnabled;
    }

    boolean isGroupsEnabled() {
        return groupsEnabled;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
