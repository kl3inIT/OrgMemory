package com.orgmemory.core.authorization;

import static com.orgmemory.core.shared.Texts.requireText;

import java.util.List;
import java.util.Objects;

public record AuthorizedResourceSetResult(
        AuthorizationQueryOutcome outcome,
        List<ResourceRef> resources,
        String reasonCode,
        String policyVersion) {

    public AuthorizedResourceSetResult {
        Objects.requireNonNull(outcome, "outcome");
        resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
        reasonCode = requireText(reasonCode, "reasonCode");
        policyVersion = requireText(policyVersion, "policyVersion");
        if (outcome == AuthorizationQueryOutcome.INDETERMINATE && !resources.isEmpty()) {
            throw new IllegalArgumentException("Indeterminate authorization results cannot contain resources");
        }
    }

    public static AuthorizedResourceSetResult resolved(List<ResourceRef> resources, String policyVersion) {
        return new AuthorizedResourceSetResult(
                AuthorizationQueryOutcome.RESOLVED,
                resources,
                "AUTHORIZED_OBJECTS_RESOLVED",
                policyVersion);
    }

    public static AuthorizedResourceSetResult indeterminate(String reasonCode, String policyVersion) {
        return new AuthorizedResourceSetResult(
                AuthorizationQueryOutcome.INDETERMINATE,
                List.of(),
                reasonCode,
                policyVersion);
    }

    public boolean resolved() {
        return outcome == AuthorizationQueryOutcome.RESOLVED;
    }

}
