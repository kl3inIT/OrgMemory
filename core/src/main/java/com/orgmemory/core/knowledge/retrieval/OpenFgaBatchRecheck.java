package com.orgmemory.core.knowledge.retrieval;

import static com.orgmemory.core.shared.Texts.requireText;

import com.orgmemory.core.authorization.BatchAuthorizationQuery;
import com.orgmemory.core.authorization.BatchAuthorizationResult;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class OpenFgaBatchRecheck {

    private final RelationshipAuthorizationSetPort authorization;

    OpenFgaBatchRecheck(RelationshipAuthorizationSetPort authorization) {
        this.authorization = Objects.requireNonNull(
                authorization,
                "authorization");
    }

    Result recheck(
            BatchAuthorizationQuery query,
            String expectedPolicyVersion,
            ResultPolicy resultPolicy,
            ReasonMapping reasons) {
        Objects.requireNonNull(query, "query");
        String expected = requireText(
                expectedPolicyVersion,
                "expectedPolicyVersion");
        Objects.requireNonNull(resultPolicy, "resultPolicy");
        Objects.requireNonNull(reasons, "reasons");

        BatchAuthorizationResult checked = authorization.batchCheck(query);
        if (!checked.resolved()) {
            return Result.failed(
                    FailureKind.UNRESOLVED,
                    reasons.unresolved().resolve(checked),
                    checked.policyVersion());
        }
        if (checked.decisions().size() != query.resources().size()) {
            return Result.failed(
                    FailureKind.DECISION_COUNT_MISMATCH,
                    reasons.decisionCountMismatch().resolve(checked),
                    checked.policyVersion());
        }
        if (!expected.equals(checked.policyVersion())) {
            return Result.failed(
                    FailureKind.OUTER_POLICY_MISMATCH,
                    reasons.outerPolicyMismatch().resolve(checked),
                    checked.policyVersion());
        }

        List<ResourceRef> allowed = new ArrayList<>();
        for (ResourceRef resource : query.resources()) {
            var decision = checked.decisions().get(resource);
            if (decision == null) {
                return Result.failed(
                        FailureKind.MISSING_DECISION,
                        reasons.missingDecision().resolve(checked),
                        checked.policyVersion());
            }
            if (!expected.equals(decision.policyVersion())) {
                return Result.failed(
                        FailureKind.DECISION_POLICY_MISMATCH,
                        reasons.decisionPolicyMismatch().resolve(checked),
                        decision.policyVersion());
            }
            if (!decision.allowed()) {
                boolean denyFails = switch (resultPolicy) {
                    case FILTER_DENIED -> false;
                    case REQUIRE_ALL_ALLOWED -> true;
                };
                if (denyFails) {
                    return Result.failed(
                            FailureKind.DENIED,
                            reasons.denied().resolve(checked),
                            checked.policyVersion());
                }
                continue;
            }
            allowed.add(resource);
        }
        return Result.allowed(allowed);
    }

    enum ResultPolicy {
        FILTER_DENIED,
        REQUIRE_ALL_ALLOWED
    }

    enum FailureKind {
        UNRESOLVED,
        DECISION_COUNT_MISMATCH,
        OUTER_POLICY_MISMATCH,
        MISSING_DECISION,
        DECISION_POLICY_MISMATCH,
        DENIED
    }

    record ReasonMapping(
            ReasonRule unresolved,
            ReasonRule decisionCountMismatch,
            ReasonRule outerPolicyMismatch,
            ReasonRule missingDecision,
            ReasonRule decisionPolicyMismatch,
            ReasonRule denied) {

        ReasonMapping {
            Objects.requireNonNull(unresolved, "unresolved");
            Objects.requireNonNull(
                    decisionCountMismatch,
                    "decisionCountMismatch");
            Objects.requireNonNull(
                    outerPolicyMismatch,
                    "outerPolicyMismatch");
            Objects.requireNonNull(missingDecision, "missingDecision");
            Objects.requireNonNull(
                    decisionPolicyMismatch,
                    "decisionPolicyMismatch");
            Objects.requireNonNull(denied, "denied");
        }
    }

    record ReasonRule(ReasonSource source, String fixedReasonCode) {

        ReasonRule {
            Objects.requireNonNull(source, "source");
            if (source == ReasonSource.FIXED) {
                fixedReasonCode = requireText(
                        fixedReasonCode,
                        "fixedReasonCode");
            } else if (fixedReasonCode != null) {
                throw new IllegalArgumentException(
                        "Result reason rules cannot define a fixed reason");
            }
        }

        static ReasonRule resultReason() {
            return new ReasonRule(ReasonSource.RESULT_REASON, null);
        }

        static ReasonRule fixed(String reasonCode) {
            return new ReasonRule(ReasonSource.FIXED, reasonCode);
        }

        String resolve(BatchAuthorizationResult result) {
            return switch (source) {
                case RESULT_REASON -> result.reasonCode();
                case FIXED -> fixedReasonCode;
            };
        }
    }

    enum ReasonSource {
        RESULT_REASON,
        FIXED
    }

    record Failure(FailureKind kind, String reasonCode, String policyVersion) {

        Failure {
            Objects.requireNonNull(kind, "kind");
            reasonCode = requireText(reasonCode, "reasonCode");
            policyVersion = requireText(policyVersion, "policyVersion");
        }
    }

    record Result(List<ResourceRef> allowedResources, Failure failure) {

        Result {
            allowedResources = List.copyOf(Objects.requireNonNull(
                    allowedResources,
                    "allowedResources"));
            if (failure != null && !allowedResources.isEmpty()) {
                throw new IllegalArgumentException(
                        "A failed result cannot contain allowed resources");
            }
        }

        static Result allowed(List<ResourceRef> resources) {
            return new Result(resources, null);
        }

        static Result failed(
                FailureKind kind,
                String reasonCode,
                String policyVersion) {
            return new Result(
                    List.of(),
                    new Failure(kind, reasonCode, policyVersion));
        }

        boolean succeeded() {
            return failure == null;
        }
    }
}
