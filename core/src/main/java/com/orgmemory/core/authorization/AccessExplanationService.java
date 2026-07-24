package com.orgmemory.core.authorization;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Answers why a principal holds a permission, for an administrator rather than for enforcement.
 *
 * <p>The verdict always comes from the check ports, which are the only authority on access. The
 * expansion is used solely to describe the relationships behind a verdict already obtained, so a
 * disagreement between the two can never widen access — at worst the explanation is empty and the
 * surface says so.
 */
public class AccessExplanationService {

    /**
     * How far a derivation may be followed. Inheritance chains in this model are three or four
     * hops; a longer walk means a cycle the engine tolerates but a human reader would not.
     */
    private static final int MAXIMUM_DEPTH = 8;

    private static final String LOCAL_POLICY_VERSION = "orgmemory-boundary-v1";

    private final RelationshipAuthorizationPort relationships;
    private final RelationshipExpansionPort expansion;
    private final Clock clock;

    public AccessExplanationService(
            RelationshipAuthorizationPort relationships,
            RelationshipExpansionPort expansion,
            Clock clock) {
        this.relationships = Objects.requireNonNull(relationships, "relationships");
        this.expansion = Objects.requireNonNull(expansion, "expansion");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Checks the relationship port directly rather than through {@link EffectiveAuthorizationService}.
     *
     * <p>That service collapses an unanswered check into a denial, which is the correct thing for
     * enforcement to do and the wrong thing to show an administrator: "no relationship grants this"
     * and "the engine did not answer" call for different actions, and only one of them is a
     * permission problem. Nothing here grants access, so preserving the third outcome costs no
     * safety. The organization guard that service applies is kept.
     */
    private AuthorizationDecision decide(
            UUID organizationId, PrincipalRef principal, PermissionKey permission, ResourceRef resource) {
        if (!Objects.equals(organizationId, resource.organizationId())) {
            return AuthorizationDecision.deny("ORGANIZATION_MISMATCH", LOCAL_POLICY_VERSION);
        }
        try {
            return relationships.check(new RelationshipAuthorizationQuery(principal, permission, resource));
        } catch (RuntimeException exception) {
            return AuthorizationDecision.indeterminate("AUTHORIZATION_ENGINE_UNAVAILABLE", LOCAL_POLICY_VERSION);
        }
    }

    /**
     * Resolves several permissions on one resource.
     *
     * <p>These are separate checks rather than one batch because a batch fixes a single relation
     * across many objects, and this asks many relations of a single object — the inverse shape.
     * The organization permission set is small and fixed, so the cost is bounded by the model.
     *
     * <p>An unresolved check yields {@link AccessState#UNKNOWN} rather than a denial, because "the
     * engine did not answer" is not "the answer was no".
     */
    public Map<PermissionKey, AccessState> effectivePermissions(
            UUID organizationId, PrincipalRef principal, List<PermissionKey> permissions, ResourceRef resource) {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(resource, "resource");
        Map<PermissionKey, AccessState> states = new LinkedHashMap<>();
        for (PermissionKey permission : List.copyOf(Objects.requireNonNull(permissions, "permissions"))) {
            AuthorizationDecision decision = decide(organizationId, principal, permission, resource);
            states.put(permission, state(decision));
        }
        return Map.copyOf(states);
    }

    private static AccessState state(AuthorizationDecision decision) {
        if (decision.outcome() == AuthorizationOutcome.INDETERMINATE) {
            return AccessState.UNKNOWN;
        }
        return decision.allowed() ? AccessState.ALLOWED : AccessState.DENIED;
    }

    public AccessExplanation explain(
            UUID organizationId, PrincipalRef principal, PermissionKey permission, ResourceRef resource) {
        return explain(organizationId, principal, permission, resource, AclProvenance.orgMemory());
    }

    /**
     * Explains one decision, letting the caller supply the ACL provenance it already knows.
     *
     * <p>An expired mirrored ACL overrides the verdict to {@link AccessState#UNKNOWN}. The engine
     * would still answer from the tuples it holds, but those tuples are a copy of a decision the
     * source system owns, and past its validity nobody can say the copy is current.
     */
    public AccessExplanation explain(
            UUID organizationId,
            PrincipalRef principal,
            PermissionKey permission,
            ResourceRef resource,
            AclProvenance provenance) {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(provenance, "provenance");
        Instant evaluatedAt = clock.instant();

        AuthorizationDecision decision = decide(organizationId, principal, permission, resource);
        if (decision.outcome() == AuthorizationOutcome.INDETERMINATE) {
            return unknown(decision.reasonCode(), provenance, decision.policyVersion(), evaluatedAt);
        }
        if (provenance.expired()) {
            return unknown("ACL_VALIDITY_EXPIRED", provenance, decision.policyVersion(), evaluatedAt);
        }

        var expanded = expansion.expand(
                new RelationshipExpansionQuery(resource, RelationName.of(permission)));
        if (!decision.allowed()) {
            return new AccessExplanation(
                    AccessState.DENIED,
                    decision.reasonCode(),
                    List.of(),
                    blocks(organizationId, expanded),
                    provenance,
                    decision.policyVersion(),
                    evaluatedAt);
        }
        List<AccessStep> path = expanded.resolved()
                ? walk(organizationId, expanded.root(), principal.openFgaUser(), new HashSet<>(), 0)
                        .orElseGet(List::of)
                : List.of();
        return new AccessExplanation(
                AccessState.ALLOWED,
                path.isEmpty() ? "GRANTED_PATH_UNAVAILABLE" : "GRANTED",
                path,
                List.of(),
                provenance,
                decision.policyVersion(),
                evaluatedAt);
    }

    /**
     * Finds the one derivation that grants access to {@code principal}.
     *
     * <p>A union is searched in order and the first satisfied branch wins, which mirrors how the
     * engine short-circuits: reporting every branch that happens to hold would suggest the answer
     * depended on all of them.
     */
    private Optional<List<AccessStep>> walk(
            UUID organizationId, ExpansionNode node, String principal, Set<String> visited, int depth) {
        if (depth > MAXIMUM_DEPTH) {
            return Optional.empty();
        }
        return switch (node) {
            case ExpansionNode.Direct direct -> direct.users().contains(principal)
                    ? Optional.of(List.of(step(direct.name(), AccessStep.AccessStepKind.DIRECT)))
                    : firstThrough(
                            organizationId,
                            direct.users(),
                            direct.name(),
                            AccessStep.AccessStepKind.DIRECT,
                            principal,
                            visited,
                            depth);
            case ExpansionNode.Computed computed -> firstThrough(
                    organizationId,
                    List.of(computed.userset()),
                    computed.name(),
                    AccessStep.AccessStepKind.COMPUTED,
                    principal,
                    visited,
                    depth);
            case ExpansionNode.TupleToUserset inherited -> firstThrough(
                    organizationId,
                    inherited.computed(),
                    inherited.name(),
                    AccessStep.AccessStepKind.INHERITED,
                    principal,
                    visited,
                    depth);
            case ExpansionNode.Union union -> union.children().stream()
                    .map(child -> walk(organizationId, child, principal, visited, depth + 1))
                    .flatMap(Optional::stream)
                    .findFirst();
            case ExpansionNode.Intersection intersection -> {
                List<AccessStep> combined = new ArrayList<>();
                for (ExpansionNode child : intersection.children()) {
                    var branch = walk(organizationId, child, principal, visited, depth + 1);
                    if (branch.isEmpty()) {
                        yield Optional.empty();
                    }
                    combined.addAll(branch.get());
                }
                yield Optional.of(List.copyOf(combined));
            }
            case ExpansionNode.Difference difference -> walk(
                            organizationId, difference.subtract(), principal, visited, depth + 1)
                    .isPresent()
                    ? Optional.empty()
                    : walk(organizationId, difference.base(), principal, visited, depth + 1);
        };
    }

    /** Follows each userset-valued member in turn, prepending the hop that reached it. */
    private Optional<List<AccessStep>> firstThrough(
            UUID organizationId,
            List<String> members,
            String name,
            AccessStep.AccessStepKind kind,
            String principal,
            Set<String> visited,
            int depth) {
        for (String member : members) {
            Optional<UsersetRef> reference = UsersetRef.parse(organizationId, member);
            if (reference.isEmpty()) {
                continue;
            }
            String key = reference.get().key();
            // `visited` guards the branch being followed right now, not everything ever seen.
            // Kept as a running set, a userset abandoned deep inside one branch would be skipped
            // in the next one, and a real grant reachable by a shorter route would be reported as
            // "path unavailable". Unwinding on the way out still breaks cycles, because a repeat
            // can only be reached while the first visit is still on the stack.
            if (!visited.add(key)) {
                continue;
            }
            try {
                var nested = expansion.expand(
                        new RelationshipExpansionQuery(
                                reference.get().resource(), reference.get().relation()));
                if (!nested.resolved()) {
                    continue;
                }
                var branch = walk(organizationId, nested.root(), principal, visited, depth + 1);
                if (branch.isPresent()) {
                    List<AccessStep> steps = new ArrayList<>();
                    steps.add(step(name, kind));
                    steps.addAll(branch.get());
                    return Optional.of(List.copyOf(steps));
                }
            } finally {
                visited.remove(key);
            }
        }
        return Optional.empty();
    }

    /**
     * Names the branches that were evaluated and did not grant access.
     *
     * <p>Everything reported here is {@code NO_RELATIONSHIP}: an explicit deny lives in the source
     * ACL ledger, not in the authorization model, so only a caller holding that ledger can add an
     * {@link AccessBlock.Kind#EXPLICIT_DENY}.
     */
    private List<AccessBlock> blocks(UUID organizationId, RelationshipExpansionResult expanded) {
        if (!expanded.resolved()) {
            return List.of(AccessBlock.missing(
                    "authorization model", "The expansion could not be read: " + expanded.reasonCode()));
        }
        List<AccessBlock> blocks = new ArrayList<>();
        collect(expanded.root(), blocks);
        return List.copyOf(blocks);
    }

    private void collect(ExpansionNode node, List<AccessBlock> blocks) {
        switch (node) {
            case ExpansionNode.Union union -> union.children().forEach(child -> collect(child, blocks));
            case ExpansionNode.Intersection intersection ->
                    intersection.children().forEach(child -> collect(child, blocks));
            case ExpansionNode.Difference difference -> collect(difference.base(), blocks);
            case ExpansionNode.Direct direct ->
                    blocks.add(AccessBlock.missing(direct.name(), "No tuple names this principal"));
            case ExpansionNode.Computed computed ->
                    blocks.add(AccessBlock.missing(computed.name(), "Not held through " + computed.userset()));
            case ExpansionNode.TupleToUserset inherited -> blocks.add(AccessBlock.missing(
                    inherited.name(), "Not inherited through " + inherited.tupleset()));
        }
    }

    private static AccessStep step(String name, AccessStep.AccessStepKind kind) {
        int hash = name.indexOf('#');
        return hash < 0
                ? new AccessStep(name, name, kind)
                : new AccessStep(name.substring(0, hash), name.substring(hash + 1), kind);
    }

    private static AccessExplanation unknown(
            String reasonCode, AclProvenance provenance, String policyVersion, Instant evaluatedAt) {
        return new AccessExplanation(
                AccessState.UNKNOWN, reasonCode, List.of(), List.of(), provenance, policyVersion, evaluatedAt);
    }
}
