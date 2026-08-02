package com.orgmemory.graphrag.testkit;

import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionCommitPermit;
import com.orgmemory.graphrag.storage.ProjectionDiscardPermit;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** Deterministic authority fixtures for adapter conformance and integration tests. */
public final class ProjectionPermitFixtures {

    private ProjectionPermitFixtures() {
    }

    public static ProjectionCommitPermit commitPermit(
            ProjectionBatch batch, Instant issuedAt) {
        return new ProjectionCommitPermit(
                id("commit", batch),
                batch.id(),
                batch.manifestFingerprint(),
                batch.claimEpoch() == 0 ? 1 : batch.claimEpoch(),
                issuedAt);
    }

    public static ProjectionDiscardPermit discardPermit(
            ProjectionBatch batch, Instant issuedAt) {
        return new ProjectionDiscardPermit(
                id("discard", batch), batch.id(), issuedAt);
    }

    private static UUID id(String kind, ProjectionBatch batch) {
        return UUID.nameUUIDFromBytes(
                (kind + ":" + batch.id()).getBytes(StandardCharsets.UTF_8));
    }
}
