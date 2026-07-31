package com.orgmemory.core.authorization;

import static com.orgmemory.core.shared.Texts.requireText;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * One access question, answered with its reasoning.
 *
 * <p>{@code path} is populated when access is granted and holds the single derivation that
 * granted it — OpenFGA short-circuits a union, so exactly one branch is decisive and a tree
 * would imply branching the answer does not contain. {@code blockedBy} is populated when access
 * is refused and names the branches that were evaluated, separating a missing relationship from
 * an explicit deny because those need different fixes.
 */
public record AccessExplanation(
        AccessState state,
        String reasonCode,
        List<AccessStep> path,
        List<AccessBlock> blockedBy,
        AclProvenance provenance,
        String policyVersion,
        Instant evaluatedAt) {

    public AccessExplanation {
        Objects.requireNonNull(state, "state");
        reasonCode = requireText(reasonCode, "reasonCode");
        path = List.copyOf(Objects.requireNonNull(path, "path"));
        blockedBy = List.copyOf(Objects.requireNonNull(blockedBy, "blockedBy"));
        // A verdict without provenance cannot be acted on: nothing tells the reader whether it
        // is a decision OrgMemory makes or a copy of one Slack or Drive owns.
        Objects.requireNonNull(provenance, "provenance");
        policyVersion = requireText(policyVersion, "policyVersion");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        if (state != AccessState.ALLOWED && !path.isEmpty()) {
            throw new IllegalArgumentException("Only a granted decision carries a derivation path");
        }
    }

}
