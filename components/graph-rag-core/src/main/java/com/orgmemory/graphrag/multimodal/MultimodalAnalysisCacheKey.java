package com.orgmemory.graphrag.multimodal;

import com.orgmemory.graphrag.processing.ResolvedDocumentProcessingProfile;
import java.util.Objects;

/** Content-addressed identity for one auditable multimodal model invocation. */
public record MultimodalAnalysisCacheKey(String value) {

    public MultimodalAnalysisCacheKey {
        value = requireSha256(value);
    }

    public static MultimodalAnalysisCacheKey create(
            MultimodalEvidenceScope scope,
            String sourceRevisionContentSha256,
            String parserVersion,
            MultimodalSidecar sidecar,
            MultimodalSidecarItem item,
            MultimodalSurroundingContext context,
            MultimodalAnalysisRoute route,
            MultimodalProcessingProfile profile) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(sidecar, "sidecar");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(profile, "profile");
        String canonical = MultimodalFingerprintInput.frame(
                scope.organizationId().toString(),
                scope.sourceRevisionId().toString(),
                scope.aclSnapshotId().toString(),
                Long.toString(scope.aclGeneration()),
                requireSha256(sourceRevisionContentSha256),
                requireText(parserVersion, "parserVersion"),
                sidecar.schemaVersion(),
                context.builderVersion(),
                item.itemId(),
                item.modality().name(),
                item.payload().contentSha256(),
                route.fingerprint(),
                profile.profileSha256());
        return new MultimodalAnalysisCacheKey(
                ResolvedDocumentProcessingProfile.sha256(canonical));
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String requireSha256(String value) {
        String normalized = requireText(value, "sha256").toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("value must be a lowercase SHA-256");
        }
        return normalized;
    }
}
