package com.orgmemory.graphrag.storage;

/**
 * Capability implemented only by stores that stage rebuildable projection
 * data before a publication-head switch.
 */
public interface StagedProjectionWriter {

    ProjectionKind projectionKind();

    void discard(ProjectionBatch batch);
}
