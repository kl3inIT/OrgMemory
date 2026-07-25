package com.orgmemory.core.authorization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Assigning and revoking the roles OrgMemory owns.
 *
 * <p>A role is tuple data, not a model relation, so granting one is a single tuple and revoking it
 * is a single deletion — neither needs a deploy. That is the whole reason {@code type role} exists
 * in the model, and it is what lets an administrator change access at runtime without touching the
 * authorization model, which is a versioned artifact.
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
    private static final String ASSIGNEE = "assignee";

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

    public RoleListing roles() {
        Map<String, List<String>> assignees = new LinkedHashMap<>();
        String continuationToken = null;
        int scanned = 0;
        int pages = 0;
        String policyVersion = tuples.policyVersion();
        boolean complete = true;
        do {
            RelationshipTuplePage page = tuples.read(PAGE_SIZE, continuationToken);
            if (!page.resolved()) {
                return new RoleListing(List.of(), false, page.policyVersion());
            }
            policyVersion = page.policyVersion();
            for (RelationshipTuple tuple : page.tuples()) {
                if (tuple.object().startsWith("role:") && ASSIGNEE.equals(tuple.relation())) {
                    assignees
                            .computeIfAbsent(tuple.object().substring("role:".length()), key -> new ArrayList<>())
                            .add(tuple.user());
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

    public RelationshipTupleWriteResult assign(String role, UUID userId) {
        return writes.write(new RelationshipTupleWriteRequest(List.of(assignment(role, userId))));
    }

    public RelationshipTupleWriteResult revoke(String role, UUID userId) {
        return tuples.delete(new RelationshipTupleWriteRequest(List.of(assignment(role, userId))));
    }

    private static RelationshipTuple assignment(String role, UUID userId) {
        return AdministrativeTupleScope.require(RelationshipTuple.of(
                PrincipalRef.user(Objects.requireNonNull(userId, "userId")).openFgaUser(),
                ASSIGNEE,
                "role:" + requireRole(role)));
    }

    private static String requireRole(String value) {
        String normalized = Objects.requireNonNull(value, "role").trim();
        if (normalized.isEmpty() || normalized.indexOf(':') >= 0 || normalized.indexOf('#') >= 0) {
            throw new IllegalArgumentException("Role must be non-empty and must not contain ':' or '#'");
        }
        return normalized;
    }
}
