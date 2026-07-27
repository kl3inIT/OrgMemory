package com.orgmemory.core.authorization;

import com.orgmemory.core.shared.error.BusinessValidationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Assigning and revoking the roles OrgMemory owns.
 *
 * <p>Organization roles are direct organization relations. This keeps every assignment
 * tenant-scoped by construction: a user assigned in one organization cannot become an assignee
 * of a globally shared role object.
 */
public class RoleAdministrationService {

    /**
     * How many tuples a role listing will read before giving up.
     *
     * <p>OpenFGA has no "list every role" call, so this pages the store. The cap keeps an
     * administrative screen from walking an arbitrarily large store, and the result says when it
     * was reached rather than presenting a truncated list as complete.
     */
    private static final int MAXIMUM_TUPLES_SCANNED = 5_000;

    /**
     * How many pages the listing will ask for regardless of what they contain.
     *
     * <p>{@link #MAXIMUM_TUPLES_SCANNED} does not bound the loop on its own: a page carrying a
     * continuation token but no tuples advances neither the count nor the cursor, and the listing
     * would spin on it. A healthy store answers the tuple cap in fifty pages, so this only ever
     * trips on a store that is handing back nothing.
     */
    private static final int MAXIMUM_PAGES_READ = 500;

    private static final int PAGE_SIZE = 100;
    private static final Map<String, String> ROLE_RELATIONS = Map.of(
            "organization-member", "member",
            "organization-admin", "administrator",
            "knowledge-reader", "knowledge_reader",
            "knowledge-contributor", "knowledge_contributor",
            "knowledge-reviewer", "knowledge_reviewer",
            "knowledge-curator", "knowledge_curator",
            "source-manager", "source_manager");
    private static final Map<String, String> RELATION_ROLES = ROLE_RELATIONS.entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getValue, Map.Entry::getKey));
    private static final Set<String> MANAGED_RELATIONS = Set.copyOf(RELATION_ROLES.keySet());

    private final RelationshipTupleWritePort writes;
    private final RelationshipTupleReconciliationPort tuples;

    public RoleAdministrationService(
            RelationshipTupleWritePort writes, RelationshipTupleReconciliationPort tuples) {
        this.writes = Objects.requireNonNull(writes, "writes");
        this.tuples = Objects.requireNonNull(tuples, "tuples");
    }

    /** One role and the principals currently assigned to it. */
    public record RoleAssignments(String role, List<String> assignees) {

        public RoleAssignments {
            Objects.requireNonNull(role, "role");
            assignees = List.copyOf(Objects.requireNonNull(assignees, "assignees"));
        }
    }

    /** {@code complete} is false when {@link #MAXIMUM_TUPLES_SCANNED} was reached before the end. */
    public record RoleListing(List<RoleAssignments> roles, boolean complete, String policyVersion) {

        public RoleListing {
            roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
            Objects.requireNonNull(policyVersion, "policyVersion");
        }
    }

    public RoleListing roles(UUID organizationId) {
        Objects.requireNonNull(organizationId, "organizationId");
        Map<String, List<String>> assignees = new LinkedHashMap<>();
        ROLE_RELATIONS.keySet().stream()
                .sorted()
                .forEach(role -> assignees.put(role, new java.util.ArrayList<>()));
        String continuationToken = null;
        int scanned = 0;
        int pages = 0;
        String policyVersion = tuples.policyVersion();
        boolean complete = true;
        do {
            RelationshipTuplePage page = tuples.read(
                    RelationshipTupleFilter.object("organization:" + organizationId),
                    PAGE_SIZE,
                    continuationToken);
            if (!page.resolved()) {
                return new RoleListing(List.of(), false, page.policyVersion());
            }
            policyVersion = page.policyVersion();
            for (RelationshipTuple tuple : page.tuples()) {
                String role = RELATION_ROLES.get(tuple.relation());
                if (role != null
                        && tuple.object().equals("organization:" + organizationId)) {
                    assignees.get(role).add(tuple.user());
                }
            }
            scanned += page.tuples().size();
            pages++;
            continuationToken = page.continuationToken();
            if (continuationToken != null
                    && (scanned >= MAXIMUM_TUPLES_SCANNED || pages >= MAXIMUM_PAGES_READ)) {
                complete = false;
                break;
            }
        } while (continuationToken != null);

        return new RoleListing(
                assignees.entrySet().stream()
                        .map(entry -> new RoleAssignments(entry.getKey(), entry.getValue()))
                        .toList(),
                complete,
                policyVersion);
    }

    public RelationshipTupleWriteResult assign(
            UUID organizationId,
            String role,
            UUID userId) {
        return writes.write(new RelationshipTupleWriteRequest(
                List.of(assignment(organizationId, role, userId))));
    }

    public RelationshipTupleWriteResult revoke(
            UUID organizationId,
            String role,
            UUID userId) {
        return tuples.delete(new RelationshipTupleWriteRequest(
                List.of(assignment(organizationId, role, userId))));
    }

    private static RelationshipTuple assignment(
            UUID organizationId,
            String role,
            UUID userId) {
        return AdministrativeTupleScope.require(RelationshipTuple.of(
                PrincipalRef.user(Objects.requireNonNull(userId, "userId")).openFgaUser(),
                relation(role),
                "organization:" + Objects.requireNonNull(organizationId, "organizationId")));
    }

    private static String relation(String value) {
        String normalized = Objects.requireNonNull(value, "role").trim();
        String relation = ROLE_RELATIONS.get(normalized);
        if (relation == null || !MANAGED_RELATIONS.contains(relation)) {
            throw new BusinessValidationException(
                    "role.unknown",
                    "Unknown organization role");
        }
        return relation;
    }
}
