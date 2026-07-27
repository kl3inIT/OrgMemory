package com.orgmemory.core.identityprovisioning;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Entity
@Table(name = "provisioning_events")
class ProvisioningEvent extends BaseEntity {

    private static final Set<String> AUDITABLE_FIELDS = Set.of(
            "active", "displayName", "emails", "externalId", "name", "userName");

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "connection_id", nullable = false, updatable = false)
    private UUID connectionId;

    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    @Column(name = "public_token_id", length = 64, updatable = false)
    private String publicTokenId;

    @Column(name = "request_id", nullable = false, length = 128, updatable = false)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private ProvisioningEventOperation operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private ProvisioningEventOutcome outcome;

    @Column(name = "reason_code", length = 64, updatable = false)
    private String reasonCode;

    @Column(name = "changed_fields", nullable = false, length = 1024, updatable = false)
    private String changedFields;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected ProvisioningEvent() {
    }

    ProvisioningEvent(
            UUID organizationId,
            UUID connectionId,
            UUID resourceId,
            String publicTokenId,
            String requestId,
            ProvisioningEventOperation operation,
            ProvisioningEventOutcome outcome,
            String reasonCode,
            Collection<String> changedFields,
            Instant occurredAt) {
        super(UUID.randomUUID());
        this.organizationId = java.util.Objects.requireNonNull(organizationId, "organizationId");
        this.connectionId = java.util.Objects.requireNonNull(connectionId, "connectionId");
        this.resourceId = resourceId;
        this.publicTokenId = optionalText(publicTokenId);
        this.requestId = requireText(requestId, "requestId");
        this.operation = java.util.Objects.requireNonNull(operation, "operation");
        this.outcome = java.util.Objects.requireNonNull(outcome, "outcome");
        this.reasonCode = optionalText(reasonCode);
        this.changedFields = auditedFieldNames(changedFields);
        this.occurredAt = java.util.Objects.requireNonNull(occurredAt, "occurredAt");
    }

    String getChangedFields() {
        return changedFields;
    }

    private static String auditedFieldNames(Collection<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return "";
        }
        TreeSet<String> safe = new TreeSet<>();
        for (String field : requested) {
            if (!AUDITABLE_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unsupported audit field name: " + field);
            }
            safe.add(field);
        }
        return String.join(",", safe);
    }

    private static String requireText(String value, String field) {
        String text = optionalText(value);
        if (text == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return text;
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
