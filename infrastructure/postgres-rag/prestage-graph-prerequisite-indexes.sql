-- Run this script directly with psql before Flyway V21 on installations where
-- the ingestion tables are large enough that a blocking UNIQUE build is unsafe.
-- Do not run it through application-owned Flyway.

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS
    ux_source_revision_graph_asset_prestage
    ON source_revisions (id, organization_id, knowledge_asset_id);

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS
    ux_source_acl_graph_generation_prestage
    ON source_acl_snapshots (id, organization_id, acl_generation);

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS
    ux_knowledge_asset_graph_acl_prestage
    ON knowledge_assets (id, organization_id, source_acl_snapshot_id);

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS
    ux_knowledge_chunk_graph_provenance_prestage
    ON knowledge_chunks (
        id,
        organization_id,
        source_revision_id,
        knowledge_asset_id,
        projection_generation
    );
