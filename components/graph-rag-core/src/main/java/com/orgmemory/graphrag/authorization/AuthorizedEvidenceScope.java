package com.orgmemory.graphrag.authorization;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable authorization result supplied to every evidence-reading contract.
 *
 * <p>The set is already resolved by the application authorization boundary.
 * Derived stores may narrow this set, but must never add an asset to it.
 */
public record AuthorizedEvidenceScope(
        UUID organizationId,
        UUID actorUserId,
        UUID actorDepartmentId,
        boolean actorExecutive,
        Set<UUID> authorizedAssetIds,
        String authorizationModelId,
        long aclGeneration,
        Instant evaluatedAt) {

    public AuthorizedEvidenceScope {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(actorUserId, "actorUserId");
        authorizedAssetIds =
                Set.copyOf(Objects.requireNonNull(authorizedAssetIds, "authorizedAssetIds"));
        authorizationModelId =
                Objects.requireNonNull(authorizationModelId, "authorizationModelId").strip();
        if (authorizationModelId.isEmpty()) {
            throw new IllegalArgumentException("authorizationModelId must not be blank");
        }
        if (aclGeneration < 0) {
            throw new IllegalArgumentException("aclGeneration must be non-negative");
        }
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    }

    public boolean includes(UUID candidateOrganizationId, UUID candidateAssetId) {
        return organizationId.equals(candidateOrganizationId)
                && authorizedAssetIds.contains(candidateAssetId);
    }

    /**
     * Stable cache partition for evidence-only retrieval results.
     *
     * <p>The actor identity is intentionally omitted: actors with the same
     * resolved evidence set, model and ACL generation may safely share a
     * retrieval result. Final answers and personalized context are outside
     * this scope. Retrieval ranking must not use department, executive status
     * or actor identity. If that invariant changes, every newly influential
     * actor attribute must join this fingerprint.
     */
    public String authorizationFingerprint() {
        MessageDigest digest = sha256();
        update(digest, organizationId.toString());
        update(digest, authorizationModelId);
        update(digest, Long.toString(aclGeneration));
        authorizedAssetIds.stream()
                .sorted()
                .map(UUID::toString)
                .forEach(assetId -> update(digest, assetId));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
