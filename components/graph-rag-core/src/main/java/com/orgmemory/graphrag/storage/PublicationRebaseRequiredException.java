package com.orgmemory.graphrag.storage;

import java.util.Objects;

/** A permitted attempt is proven unreachable and must retire authority before cleanup. */
public final class PublicationRebaseRequiredException extends RuntimeException {

    private final ProjectionBatch batch;
    private final ProjectionDiscardPermit discardPermit;

    public PublicationRebaseRequiredException(
            ProjectionBatch batch,
            ProjectionDiscardPermit discardPermit,
            RuntimeException cause) {
        super("publication attempt lost its exact predecessor and requires rebase", cause);
        this.batch = Objects.requireNonNull(batch, "batch");
        this.discardPermit = Objects.requireNonNull(discardPermit, "discardPermit");
        discardPermit.requireAuthorizes(batch);
    }

    public ProjectionBatch batch() {
        return batch;
    }

    public ProjectionDiscardPermit discardPermit() {
        return discardPermit;
    }
}
