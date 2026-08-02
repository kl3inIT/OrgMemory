package com.orgmemory.graphrag.storage;

import java.util.Objects;
import java.util.Optional;

/** Result of proving whether an unpublished attempt is safe to discard. */
public record ProjectionAbortOutcome(
        Status status,
        ProjectionDiscardPermit discardPermit,
        ProjectionSnapshot publishedSnapshot) {

    public ProjectionAbortOutcome {
        Objects.requireNonNull(status, "status");
        if (status == Status.DISCARD_ALLOWED && discardPermit == null) {
            throw new IllegalArgumentException("discard permit is required");
        }
        if (status == Status.PUBLISHED && publishedSnapshot == null) {
            throw new IllegalArgumentException("published snapshot is required");
        }
        if (status != Status.DISCARD_ALLOWED && discardPermit != null) {
            throw new IllegalArgumentException("discard permit is only valid for discard");
        }
        if (status != Status.PUBLISHED && publishedSnapshot != null) {
            throw new IllegalArgumentException("snapshot is only valid for published outcome");
        }
    }

    public static ProjectionAbortOutcome discardAllowed(ProjectionDiscardPermit permit) {
        return new ProjectionAbortOutcome(Status.DISCARD_ALLOWED, permit, null);
    }

    public static ProjectionAbortOutcome keepStaging() {
        return new ProjectionAbortOutcome(Status.KEEP_STAGING, null, null);
    }

    public static ProjectionAbortOutcome published(ProjectionSnapshot snapshot) {
        return new ProjectionAbortOutcome(Status.PUBLISHED, null, snapshot);
    }

    public Optional<ProjectionDiscardPermit> discardPermitOptional() {
        return Optional.ofNullable(discardPermit);
    }

    public Optional<ProjectionSnapshot> publishedSnapshotOptional() {
        return Optional.ofNullable(publishedSnapshot);
    }

    public enum Status {
        DISCARD_ALLOWED,
        KEEP_STAGING,
        PUBLISHED
    }
}
