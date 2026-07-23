CREATE TABLE graph_index_jobs (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    knowledge_asset_id uuid NOT NULL,
    knowledge_asset_version_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    projection_generation bigint NOT NULL,
    job_type varchar(64) NOT NULL,
    status varchar(32) NOT NULL,
    available_at timestamptz NOT NULL,
    lease_owner varchar(128),
    lease_until timestamptz,
    attempt_count integer NOT NULL,
    max_attempts integer NOT NULL,
    last_error_code varchar(64),
    last_error_message varchar(512),
    completed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_graph_index_job_version
        UNIQUE (knowledge_asset_version_id),
    CONSTRAINT fk_graph_index_job_asset
        FOREIGN KEY (knowledge_asset_id, organization_id)
        REFERENCES knowledge_assets(id, organization_id),
    CONSTRAINT fk_graph_index_job_asset_version
        FOREIGN KEY (
            knowledge_asset_version_id,
            organization_id,
            knowledge_asset_id
        )
        REFERENCES knowledge_asset_versions(id, organization_id, knowledge_asset_id),
    CONSTRAINT fk_graph_index_job_source_revision
        FOREIGN KEY (
            source_revision_id,
            organization_id,
            knowledge_asset_id,
            knowledge_asset_version_id
        )
        REFERENCES source_revisions(
            id,
            organization_id,
            knowledge_asset_id,
            knowledge_asset_version_id
        ),
    CONSTRAINT chk_graph_index_job_type
        CHECK (job_type = 'INDEX_KNOWLEDGE_ASSET_VERSION'),
    CONSTRAINT chk_graph_index_job_status
        CHECK (status IN (
            'PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'SUPERSEDED'
        )),
    CONSTRAINT chk_graph_index_job_generation
        CHECK (projection_generation > 0),
    CONSTRAINT chk_graph_index_job_attempts
        CHECK (
            attempt_count >= 0
            AND max_attempts > 0
            AND attempt_count <= max_attempts
        ),
    CONSTRAINT chk_graph_index_job_lease
        CHECK (
            (
                status = 'PROCESSING'
                AND lease_owner IS NOT NULL
                AND lease_until IS NOT NULL
            )
            OR (
                status <> 'PROCESSING'
                AND lease_owner IS NULL
                AND lease_until IS NULL
            )
        ),
    CONSTRAINT chk_graph_index_job_completion
        CHECK (
            (
                status IN ('SUCCEEDED', 'FAILED', 'SUPERSEDED')
                AND completed_at IS NOT NULL
            )
            OR (
                status IN ('PENDING', 'PROCESSING')
                AND completed_at IS NULL
            )
        )
);

CREATE INDEX idx_graph_index_job_claim
    ON graph_index_jobs (status, available_at, created_at)
    WHERE status IN ('PENDING', 'PROCESSING');

CREATE INDEX idx_graph_index_job_expired_lease
    ON graph_index_jobs (lease_until)
    WHERE status = 'PROCESSING';

-- Existing current source-backed versions become recoverable work after upgrade.
-- New publications insert the same job in their activation transaction.
INSERT INTO graph_index_jobs (
    id,
    organization_id,
    knowledge_asset_id,
    knowledge_asset_version_id,
    source_revision_id,
    projection_generation,
    job_type,
    status,
    available_at,
    lease_owner,
    lease_until,
    attempt_count,
    max_attempts,
    last_error_code,
    last_error_message,
    completed_at,
    created_at,
    updated_at,
    version
)
SELECT
    gen_random_uuid(),
    asset.organization_id,
    asset.id,
    asset_version.id,
    asset_version.source_revision_id,
    publication.projection_generation,
    'INDEX_KNOWLEDGE_ASSET_VERSION',
    'PENDING',
    now(),
    NULL,
    NULL,
    0,
    5,
    NULL,
    NULL,
    NULL,
    now(),
    now(),
    0
FROM knowledge_assets asset
JOIN knowledge_asset_versions asset_version
  ON asset_version.id = asset.current_version_id
 AND asset_version.organization_id = asset.organization_id
 AND asset_version.knowledge_asset_id = asset.id
 AND asset_version.status = 'ACTIVE'
JOIN source_revisions revision
  ON revision.id = asset_version.source_revision_id
 AND revision.organization_id = asset.organization_id
 AND revision.knowledge_asset_id = asset.id
 AND revision.knowledge_asset_version_id = asset_version.id
 AND revision.status = 'READY'
JOIN knowledge_asset_publication_outbox publication
  ON publication.knowledge_asset_version_id = asset_version.id
 AND publication.organization_id = asset.organization_id
 AND publication.knowledge_asset_id = asset.id
 AND publication.status = 'APPLIED'
WHERE asset.archived_at IS NULL
ON CONFLICT (knowledge_asset_version_id) DO NOTHING;
