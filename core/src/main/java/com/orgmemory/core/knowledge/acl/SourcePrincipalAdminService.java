package com.orgmemory.core.knowledge.acl;

import com.orgmemory.core.knowledge.connector.ActiveGroupMembershipRow;
import com.orgmemory.core.knowledge.retrieval.KnowledgeResourceNotFoundException;
import com.orgmemory.core.knowledge.connector.SourceConnection;
import com.orgmemory.core.knowledge.connector.SourceConnectionRepository;
import com.orgmemory.core.knowledge.connector.SourceConnectionView;
import com.orgmemory.core.knowledge.acl.SourceGroupView;
import com.orgmemory.core.knowledge.connector.SourceIdentityTrust;

import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.core.shared.error.BusinessConflictException;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The administration view over the external identity ledger. Reads join observed
 * principals to the internal users they resolve to; writes go through
 * {@link SourcePrincipalMappingService} so the tier rules, the one-active-mapping
 * invariant, and the ledger audit trail are never duplicated here.
 *
 * <p>Nothing in this service widens access on its own. Confirming a mapping makes an
 * already sealed grant resolvable; it never creates a grant.
 */
@Service
public class SourcePrincipalAdminService {

    static final String POLICY_VERSION = "permissions-admin-v1";

    private final SourcePrincipalRepository principals;
    private final SourcePrincipalMappingRepository mappings;
    private final SourceConnectionRepository connections;
    private final SourceGroupMembershipHeadRepository membershipHeads;
    private final SourcePrincipalMappingService mappingService;
    private final AppUserRepository users;
    private final PermissionAuditService audit;

    SourcePrincipalAdminService(
            SourcePrincipalRepository principals,
            SourcePrincipalMappingRepository mappings,
            SourceConnectionRepository connections,
            SourceGroupMembershipHeadRepository membershipHeads,
            SourcePrincipalMappingService mappingService,
            AppUserRepository users,
            PermissionAuditService audit) {
        this.principals = principals;
        this.mappings = mappings;
        this.connections = connections;
        this.membershipHeads = membershipHeads;
        this.mappingService = mappingService;
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<SourcePrincipalView> listPrincipals(UUID organizationId) {
        Map<UUID, SourcePrincipalMappingView> byPrincipal = activeMappings(organizationId);
        return principals.findByOrganizationId(organizationId).stream()
                .map(principal -> view(principal, byPrincipal.get(principal.getId())))
                .sorted(PRINCIPAL_ORDER)
                .toList();
    }

    /** How many observed source identities each internal user currently answers for. */
    @Transactional(readOnly = true)
    public Map<UUID, Integer> mappedPrincipalCountByUser(UUID organizationId) {
        Map<UUID, Integer> counts = new HashMap<>();
        mappings.findByOrganizationIdAndStatus(organizationId, SourcePrincipalMappingStatus.ACTIVE)
                .forEach(mapping -> counts.merge(mapping.getAppUserId(), 1, Integer::sum));
        return counts;
    }

    @Transactional(readOnly = true)
    public List<SourceConnectionView> listConnections(UUID organizationId) {
        Map<UUID, SourcePrincipalMappingView> byPrincipal = activeMappings(organizationId);
        Map<ConnectionKey, SourceConnection> decided = connections.findByOrganizationId(organizationId).stream()
                .collect(Collectors.toMap(ConnectionKey::ofConnection, Function.identity()));

        Map<ConnectionKey, ConnectionTally> tallies = new HashMap<>();
        for (SourcePrincipal principal : principals.findByOrganizationId(organizationId)) {
            tallies.computeIfAbsent(ConnectionKey.ofPrincipal(principal), key -> new ConnectionTally())
                    .observe(principal, byPrincipal.containsKey(principal.getId()));
        }
        // A connection ruled on before its first crawl still deserves a row.
        decided.keySet().forEach(key -> tallies.computeIfAbsent(key, unused -> new ConnectionTally()));

        return tallies.entrySet().stream()
                .map(entry -> entry.getValue().toView(entry.getKey(), decided.get(entry.getKey())))
                .sorted(CONNECTION_ORDER)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SourceGroupView> listGroups(UUID organizationId) {
        Map<UUID, SourcePrincipal> byId = principals.findByOrganizationId(organizationId).stream()
                .collect(Collectors.toMap(SourcePrincipal::getId, Function.identity()));
        Map<UUID, SourcePrincipalMappingView> byPrincipal = activeMappings(organizationId);
        Map<UUID, List<ActiveGroupMembershipRow>> active =
                membershipHeads.findActiveMembership(organizationId).stream()
                        .collect(Collectors.groupingBy(
                                ActiveGroupMembershipRow::groupPrincipalId));

        return byId.values().stream()
                .filter(principal -> principal.getKind() == SourcePrincipalKind.SOURCE_GROUP)
                .map(group -> groupView(group, active.get(group.getId()), byId, byPrincipal))
                .sorted(GROUP_ORDER)
                .toList();
    }

    @Transactional
    public SourcePrincipalView confirmMapping(UUID organizationId, UUID principalId, UUID appUserId, UUID adminUserId) {
        // A confirmation without a target user is an incomplete command, not a lookup for
        // nobody: reject it here so it fails as a bad request instead of reaching the
        // repository, where a null identifier surfaces as a server error.
        if (appUserId == null) {
            throw new BusinessValidationException(
                    "source-principal.mapping-user-required",
                    "An internal user is required to confirm a mapping");
        }
        SourcePrincipal principal = requirePrincipal(organizationId, principalId);
        // The mapping service refuses a second active mapping with an IllegalStateException.
        // Reject it at the use-case boundary as an explicit conflict.
        mappings.findBySourcePrincipalIdAndStatus(principalId, SourcePrincipalMappingStatus.ACTIVE)
                .filter(active -> !active.getAppUserId().equals(appUserId))
                .ifPresent(active -> {
                    throw new BusinessConflictException(
                            "source-principal.mapping-conflict",
                            "This principal is already mapped to another user; revoke that mapping first");
                });

        mappingService.adminConfirm(principal, appUserId, "admin-confirm:" + adminUserId);
        recordAdminAction(organizationId, adminUserId, principalId, "SOURCE_PRINCIPAL_ADMIN_CONFIRM", "ADMIN_CONFIRMED");
        return view(principal, activeMapping(principalId).orElse(null));
    }

    @Transactional
    public SourcePrincipalView revokeMapping(UUID organizationId, UUID principalId, UUID adminUserId) {
        SourcePrincipal principal = requirePrincipal(organizationId, principalId);
        mappingService.revoke(organizationId, principalId);
        recordAdminAction(organizationId, adminUserId, principalId, "SOURCE_PRINCIPAL_ADMIN_REVOKE", "ADMIN_REVOKED");
        return view(principal, activeMapping(principalId).orElse(null));
    }

    /**
     * Records the standing trust decision for a connection. Raising trust maps nobody
     * retroactively: the next crawl observes principals under the new decision and the
     * existing tiers do the resolving.
     */
    @Transactional
    public SourceConnectionView setIdentityTrust(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            SourceIdentityTrust identityTrust,
            UUID decidedByUserId) {
        if (identityTrust == null) {
            throw new BusinessValidationException(
                    "source-connection.identity-trust-required",
                    "An identity trust level is required");
        }
        String system = requireText(sourceSystem, "source system");
        String connectionKey = requireText(sourceConnectionKey, "source connection key");
        if (!isActiveInOrg(decidedByUserId, organizationId)) {
            throw new BusinessValidationException(
                    "source-connection.deciding-user-invalid",
                    "The deciding user is not active in this organization");
        }

        SourceConnection connection = connections
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKey(organizationId, system, connectionKey)
                .orElseGet(() -> new SourceConnection(organizationId, system, connectionKey));
        connection.decideTrust(identityTrust, decidedByUserId, Instant.now());
        connections.save(connection);

        audit.record(new PermissionAuditCommand(
                organizationId,
                decidedByUserId,
                "SOURCE_CONNECTION_TRUST",
                "SOURCE_CONNECTION",
                system + "/" + connectionKey,
                PermissionAuditDecision.ALLOW,
                "IDENTITY_TRUST_" + identityTrust.name(),
                POLICY_VERSION,
                null,
                null));

        return listConnections(organizationId).stream()
                .filter(view -> view.sourceSystem().equals(system)
                        && view.sourceConnectionKey().equals(connectionKey))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The trust decision was not persisted"));
    }

    private void recordAdminAction(
            UUID organizationId, UUID adminUserId, UUID principalId, String operation, String reasonCode) {
        // The mapping service audits who was affected; this event records who acted.
        audit.record(new PermissionAuditCommand(
                organizationId,
                adminUserId,
                operation,
                "SOURCE_PRINCIPAL",
                principalId.toString(),
                PermissionAuditDecision.ALLOW,
                reasonCode,
                POLICY_VERSION,
                null,
                null));
    }

    private SourcePrincipal requirePrincipal(UUID organizationId, UUID principalId) {
        return principals.findByIdAndOrganizationId(principalId, organizationId)
                .orElseThrow(KnowledgeResourceNotFoundException::new);
    }

    private Optional<SourcePrincipalMappingView> activeMapping(UUID principalId) {
        return mappings.findBySourcePrincipalIdAndStatus(principalId, SourcePrincipalMappingStatus.ACTIVE)
                .map(mapping -> mappingView(mapping, users.findById(mapping.getAppUserId()).orElse(null)));
    }

    private Map<UUID, SourcePrincipalMappingView> activeMappings(UUID organizationId) {
        List<SourcePrincipalMapping> active =
                mappings.findByOrganizationIdAndStatus(organizationId, SourcePrincipalMappingStatus.ACTIVE);
        Map<UUID, AppUser> byUserId = users
                .findAllById(active.stream().map(SourcePrincipalMapping::getAppUserId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        return active.stream().collect(Collectors.toMap(
                SourcePrincipalMapping::getSourcePrincipalId,
                mapping -> mappingView(mapping, byUserId.get(mapping.getAppUserId()))));
    }

    private static SourceGroupView groupView(
            SourcePrincipal group,
            List<ActiveGroupMembershipRow> membership,
            Map<UUID, SourcePrincipal> byId,
            Map<UUID, SourcePrincipalMappingView> byPrincipal) {
        List<ActiveGroupMembershipRow> rows = membership == null ? List.of() : membership;
        ActiveGroupMembershipRow sealed = rows.isEmpty() ? null : rows.getFirst();
        return new SourceGroupView(
                group.getId(),
                group.getSourceSystem(),
                group.getSourceConnectionKey(),
                group.getNativePrincipalId(),
                group.getObservedDisplayName(),
                sealed == null ? null : sealed.membershipSnapshotId(),
                sealed == null ? 0L : sealed.membershipGeneration(),
                sealed == null ? null : sealed.sealedAt(),
                rows.stream()
                        .map(row -> memberView(
                                byId.get(row.memberPrincipalId()), byPrincipal.get(row.memberPrincipalId())))
                        .filter(Objects::nonNull)
                        .sorted(MEMBER_ORDER)
                        .toList());
    }

    private static SourceGroupView.SourceGroupMemberView memberView(
            SourcePrincipal member, SourcePrincipalMappingView mapping) {
        if (member == null) {
            return null;
        }
        return new SourceGroupView.SourceGroupMemberView(
                member.getId(),
                member.getNativePrincipalId(),
                member.getObservedDisplayName(),
                member.getObservedEmail(),
                mapping == null ? null : mapping.appUserId(),
                mapping == null ? null : mapping.appUserName());
    }

    private static SourcePrincipalView view(SourcePrincipal principal, SourcePrincipalMappingView mapping) {
        return new SourcePrincipalView(
                principal.getId(),
                principal.getSourceSystem(),
                principal.getSourceConnectionKey(),
                principal.getNativePrincipalId(),
                principal.getKind(),
                principal.getObservedEmail(),
                principal.getObservedDisplayName(),
                principal.isSsoVerified(),
                principal.getLastSeenAt(),
                mapping);
    }

    private static SourcePrincipalMappingView mappingView(SourcePrincipalMapping mapping, AppUser user) {
        return new SourcePrincipalMappingView(
                mapping.getId(),
                mapping.getAppUserId(),
                user == null ? null : user.getName(),
                user == null ? null : user.getEmail(),
                mapping.getMethod(),
                mapping.getStatus(),
                mapping.getEvidence(),
                mapping.getVerifiedAt());
    }

    private boolean isActiveInOrg(UUID appUserId, UUID organizationId) {
        return appUserId != null
                && users.findById(appUserId)
                        .map(user -> user.isActive() && user.getOrganizationId().equals(organizationId))
                        .orElse(false);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessValidationException(
                    "source-connection.identifier-required",
                    "A " + field + " is required");
        }
        return value;
    }

    /** Unmapped principals first: those are the ones currently denying. */
    private static final Comparator<SourcePrincipalView> PRINCIPAL_ORDER =
            Comparator.comparing((SourcePrincipalView view) -> view.mapped())
                    .thenComparing((SourcePrincipalView view) -> view.sourceSystem())
                    .thenComparing((SourcePrincipalView view) -> view.sourceConnectionKey())
                    .thenComparing((SourcePrincipalView view) -> view.kind())
                    .thenComparing((SourcePrincipalView view) -> view.nativePrincipalId());

    private static final Comparator<SourceConnectionView> CONNECTION_ORDER =
            Comparator.comparing((SourceConnectionView view) -> view.sourceSystem())
                    .thenComparing((SourceConnectionView view) -> view.sourceConnectionKey());

    private static final Comparator<SourceGroupView> GROUP_ORDER =
            Comparator.comparing((SourceGroupView view) -> view.sourceSystem())
                    .thenComparing((SourceGroupView view) -> view.sourceConnectionKey())
                    .thenComparing((SourceGroupView view) -> view.nativePrincipalId());

    private static final Comparator<SourceGroupView.SourceGroupMemberView> MEMBER_ORDER =
            Comparator.comparing(
                    (SourceGroupView.SourceGroupMemberView view) -> view.nativePrincipalId());

    private record ConnectionKey(String sourceSystem, String sourceConnectionKey) {

        static ConnectionKey ofPrincipal(SourcePrincipal principal) {
            return new ConnectionKey(principal.getSourceSystem(), principal.getSourceConnectionKey());
        }

        static ConnectionKey ofConnection(SourceConnection connection) {
            return new ConnectionKey(connection.getSourceSystem(), connection.getSourceConnectionKey());
        }
    }

    private static final class ConnectionTally {

        private int userCount;
        private int mappedUserCount;
        private int groupCount;
        private Instant lastSeenAt;

        void observe(SourcePrincipal principal, boolean mapped) {
            if (principal.getKind() == SourcePrincipalKind.SOURCE_GROUP) {
                groupCount++;
            } else {
                userCount++;
                if (mapped) {
                    mappedUserCount++;
                }
            }
            if (lastSeenAt == null || principal.getLastSeenAt().isAfter(lastSeenAt)) {
                lastSeenAt = principal.getLastSeenAt();
            }
        }

        SourceConnectionView toView(ConnectionKey key, SourceConnection decision) {
            return new SourceConnectionView(
                    key.sourceSystem(),
                    key.sourceConnectionKey(),
                    decision == null ? SourceIdentityTrust.UNTRUSTED : decision.getIdentityTrust(),
                    decision == null ? null : decision.getTrustDecidedByUserId(),
                    decision == null ? null : decision.getTrustDecidedAt(),
                    userCount,
                    mappedUserCount,
                    groupCount,
                    lastSeenAt);
        }
    }
}
