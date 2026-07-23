-- Split the stable OpenFGA resource identity from immutable knowledge content.
-- This development baseline intentionally discards the pre-versioned Knowledge
-- Asset and upload projections. Source files must be imported again after V22;
-- no compatibility backfill or legacy identity mapping is retained.
--
-- Deployment contract: V22 requires a maintenance window. It renames and drops
-- tables, rebuilds derived graph projections, validates new constraints, and
-- creates regular indexes inside Flyway's transaction. Large installations must
-- pause API/worker writes and may pre-stage equivalent concurrent indexes before
-- enabling this migration.

TRUNCATE TABLE
    graph_entity_embeddings,
    graph_relation_embeddings,
    graph_entity_contributions,
    graph_relation_contributions,
    graph_projection_heads,
    knowledge_chunks,
    knowledge_asset_publication_outbox,
    source_ingestion_jobs,
    source_revisions,
    source_objects,
    evidence_blobs,
    knowledge_assets;

ALTER TABLE graph_entity_contributions
    DROP CONSTRAINT IF EXISTS fk_graph_entity_contribution_asset_acl;
ALTER TABLE graph_relation_contributions
    DROP CONSTRAINT IF EXISTS fk_graph_relation_contribution_asset_acl;
ALTER TABLE knowledge_chunks
    DROP CONSTRAINT IF EXISTS fk_knowledge_chunk_asset_organization;
ALTER TABLE knowledge_asset_publication_outbox
    DROP CONSTRAINT IF EXISTS fk_knowledge_asset_publication_asset;
ALTER TABLE source_revisions
    DROP CONSTRAINT IF EXISTS source_revisions_knowledge_asset_id_fkey;
ALTER TABLE knowledge_assets
    DROP CONSTRAINT IF EXISTS uq_knowledge_asset_graph_acl;

ALTER TABLE knowledge_assets RENAME TO knowledge_asset_versions;
ALTER INDEX IF EXISTS idx_knowledge_status RENAME TO idx_knowledge_asset_version_status;
ALTER INDEX IF EXISTS idx_knowledge_classification RENAME TO idx_knowledge_asset_version_classification;
ALTER INDEX IF EXISTS idx_knowledge_department RENAME TO idx_knowledge_asset_version_department;
ALTER INDEX IF EXISTS idx_knowledge_asset_space RENAME TO idx_knowledge_asset_version_space;

CREATE TABLE knowledge_assets (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    knowledge_space_id uuid NOT NULL,
    source_object_id uuid,
    current_version_id uuid,
    archived_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_knowledge_asset_id_organization
        UNIQUE (id, organization_id),
    CONSTRAINT uq_knowledge_asset_source
        UNIQUE (organization_id, source_object_id),
    CONSTRAINT fk_knowledge_asset_space_organization
        FOREIGN KEY (knowledge_space_id, organization_id)
        REFERENCES knowledge_spaces(id, organization_id),
    CONSTRAINT fk_knowledge_asset_source_organization
        FOREIGN KEY (source_object_id, organization_id)
        REFERENCES source_objects(id, organization_id),
    CONSTRAINT chk_knowledge_asset_archive_head CHECK (
        archived_at IS NULL OR current_version_id IS NULL
    )
);

ALTER TABLE knowledge_asset_versions
    ADD COLUMN knowledge_asset_id uuid,
    ADD COLUMN version_number bigint,
    ADD COLUMN source_revision_id uuid;

ALTER TABLE knowledge_asset_versions
    ALTER COLUMN knowledge_asset_id SET NOT NULL,
    ALTER COLUMN version_number SET NOT NULL,
    ADD CONSTRAINT uq_knowledge_asset_version_number
        UNIQUE (knowledge_asset_id, version_number),
    ADD CONSTRAINT uq_knowledge_asset_version_id_organization
        UNIQUE (id, organization_id),
    ADD CONSTRAINT uq_knowledge_asset_version_chain
        UNIQUE (id, organization_id, knowledge_asset_id),
    ADD CONSTRAINT fk_knowledge_asset_version_asset
        FOREIGN KEY (knowledge_asset_id, organization_id)
        REFERENCES knowledge_assets(id, organization_id),
    ADD CONSTRAINT fk_knowledge_asset_version_revision
        FOREIGN KEY (source_revision_id, organization_id)
        REFERENCES source_revisions(id, organization_id),
    ADD CONSTRAINT chk_knowledge_asset_version_number
        CHECK (version_number > 0);

CREATE UNIQUE INDEX uq_knowledge_asset_one_active_version
    ON knowledge_asset_versions (knowledge_asset_id)
    WHERE status = 'ACTIVE';

ALTER TABLE knowledge_assets
    ADD CONSTRAINT fk_knowledge_asset_current_version
        FOREIGN KEY (current_version_id, organization_id, id)
        REFERENCES knowledge_asset_versions(id, organization_id, knowledge_asset_id);

ALTER TABLE source_revisions
    ADD COLUMN knowledge_asset_version_id uuid;

ALTER TABLE source_revisions
    ADD CONSTRAINT fk_source_revision_knowledge_asset
        FOREIGN KEY (knowledge_asset_id, organization_id)
        REFERENCES knowledge_assets(id, organization_id),
    ADD CONSTRAINT fk_source_revision_knowledge_asset_version
        FOREIGN KEY (knowledge_asset_version_id, organization_id, knowledge_asset_id)
        REFERENCES knowledge_asset_versions(id, organization_id, knowledge_asset_id),
    ADD CONSTRAINT uq_source_revision_asset_version
        UNIQUE (id, organization_id, knowledge_asset_id, knowledge_asset_version_id);

ALTER TABLE source_objects
    ADD COLUMN latest_revision_id uuid;

ALTER TABLE source_objects
    ADD CONSTRAINT fk_source_object_latest_revision
        FOREIGN KEY (latest_revision_id, organization_id, id)
        REFERENCES source_revisions(id, organization_id, source_object_id);

ALTER TABLE knowledge_chunks
    ADD COLUMN knowledge_asset_version_id uuid;

ALTER TABLE knowledge_chunks
    ALTER COLUMN knowledge_asset_version_id SET NOT NULL,
    ADD CONSTRAINT fk_knowledge_chunk_asset_organization
        FOREIGN KEY (knowledge_asset_id, organization_id)
        REFERENCES knowledge_assets(id, organization_id),
    ADD CONSTRAINT fk_knowledge_chunk_asset_version
        FOREIGN KEY (
            knowledge_asset_version_id,
            organization_id,
            knowledge_asset_id
        )
        REFERENCES knowledge_asset_versions(id, organization_id, knowledge_asset_id),
    ADD CONSTRAINT uq_knowledge_chunk_version_provenance
        UNIQUE (
            id,
            organization_id,
            source_revision_id,
            knowledge_asset_id,
            knowledge_asset_version_id,
            projection_generation
        );

ALTER TABLE knowledge_asset_publication_outbox
    DROP CONSTRAINT IF EXISTS uq_knowledge_asset_publication_asset,
    ADD COLUMN knowledge_asset_version_id uuid;

ALTER TABLE knowledge_asset_publication_outbox
    ALTER COLUMN knowledge_asset_version_id SET NOT NULL,
    ADD CONSTRAINT uq_knowledge_asset_publication_version
        UNIQUE (knowledge_asset_version_id),
    ADD CONSTRAINT fk_knowledge_asset_publication_asset
        FOREIGN KEY (knowledge_asset_id, organization_id)
        REFERENCES knowledge_assets(id, organization_id),
    ADD CONSTRAINT fk_knowledge_asset_publication_version
        FOREIGN KEY (
            knowledge_asset_version_id,
            organization_id,
            knowledge_asset_id
        )
        REFERENCES knowledge_asset_versions(id, organization_id, knowledge_asset_id);

CREATE TABLE knowledge_asset_evidence_links (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    knowledge_asset_version_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    source_acl_snapshot_id uuid NOT NULL,
    evidence_role varchar(32) NOT NULL,
    span_start integer,
    span_end integer,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_knowledge_asset_evidence
        UNIQUE (knowledge_asset_version_id, source_revision_id, source_acl_snapshot_id),
    CONSTRAINT fk_knowledge_asset_evidence_version
        FOREIGN KEY (knowledge_asset_version_id, organization_id)
        REFERENCES knowledge_asset_versions(id, organization_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_knowledge_asset_evidence_revision
        FOREIGN KEY (source_revision_id, organization_id)
        REFERENCES source_revisions(id, organization_id),
    CONSTRAINT fk_knowledge_asset_evidence_acl
        FOREIGN KEY (source_acl_snapshot_id, organization_id)
        REFERENCES source_acl_snapshots(id, organization_id),
    CONSTRAINT chk_knowledge_asset_evidence_role
        CHECK (evidence_role IN ('PRIMARY', 'SUPPORTING')),
    CONSTRAINT chk_knowledge_asset_evidence_span CHECK (
        (span_start IS NULL AND span_end IS NULL)
        OR (
            span_start IS NOT NULL
            AND span_end IS NOT NULL
            AND span_start >= 0
            AND span_end > span_start
        )
    )
);

INSERT INTO knowledge_asset_evidence_links (
    id,
    organization_id,
    knowledge_asset_version_id,
    source_revision_id,
    source_acl_snapshot_id,
    evidence_role,
    span_start,
    span_end,
    created_at,
    updated_at,
    version
)
SELECT
    gen_random_uuid(),
    version_row.organization_id,
    version_row.id,
    version_row.source_revision_id,
    version_row.source_acl_snapshot_id,
    'PRIMARY',
    NULL,
    NULL,
    now(),
    now(),
    0
FROM knowledge_asset_versions version_row
WHERE version_row.source_revision_id IS NOT NULL;

CREATE INDEX idx_knowledge_asset_space
    ON knowledge_assets (organization_id, knowledge_space_id, updated_at DESC);
CREATE INDEX idx_knowledge_asset_current_version
    ON knowledge_assets (organization_id, current_version_id)
    WHERE current_version_id IS NOT NULL;
CREATE INDEX idx_knowledge_asset_version_asset
    ON knowledge_asset_versions (organization_id, knowledge_asset_id, version_number DESC);
CREATE INDEX idx_knowledge_asset_evidence_revision
    ON knowledge_asset_evidence_links (organization_id, source_revision_id);

-- Graph contribution rows remain rebuildable. Their source revision now pins an
-- immutable asset version; the stable asset ID remains the authorization key.
ALTER TABLE graph_projection_heads
    DROP CONSTRAINT IF EXISTS fk_graph_projection_revision_asset,
    ADD CONSTRAINT fk_graph_projection_revision_asset
        FOREIGN KEY (source_revision_id, organization_id, knowledge_asset_id)
        REFERENCES source_revisions(id, organization_id, knowledge_asset_id)
        ON DELETE CASCADE;

-- Remove the retired Capability Asset prototype. Historical migrations remain
-- immutable so existing databases can upgrade through this forward cleanup.
DROP TABLE IF EXISTS asset_embeddings;
DROP TABLE IF EXISTS asset_tags;
DROP TABLE IF EXISTS asset_approval_events;
DROP TABLE IF EXISTS asset_usage_events;
DROP TABLE IF EXISTS asset_versions;
DROP TABLE IF EXISTS capability_assets;
DROP TABLE IF EXISTS tags;
