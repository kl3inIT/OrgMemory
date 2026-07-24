ALTER TABLE graph_index_jobs
    ADD COLUMN idempotency_key varchar(255),
    ADD COLUMN manifest_fingerprint varchar(64),
    ADD COLUMN cancellation_requested boolean NOT NULL DEFAULT false,
    ADD COLUMN cancellation_requested_at timestamptz;

UPDATE graph_index_jobs
SET idempotency_key =
        'graph:' || organization_id || ':' || source_revision_id || ':' || projection_generation
WHERE idempotency_key IS NULL;

ALTER TABLE graph_index_jobs
    ALTER COLUMN idempotency_key SET NOT NULL,
    DROP CONSTRAINT chk_graph_index_job_status,
    DROP CONSTRAINT chk_graph_index_job_completion;

ALTER TABLE graph_index_jobs
    ADD CONSTRAINT chk_graph_index_job_status
        CHECK (status IN (
            'PENDING',
            'PROCESSING',
            'SUCCEEDED',
            'FAILED',
            'SUPERSEDED',
            'CANCELLED'
        )),
    ADD CONSTRAINT chk_graph_index_job_completion
        CHECK (
            (
                status IN ('SUCCEEDED', 'FAILED', 'SUPERSEDED', 'CANCELLED')
                AND completed_at IS NOT NULL
            )
            OR (
                status IN ('PENDING', 'PROCESSING')
                AND completed_at IS NULL
            )
        ),
    ADD CONSTRAINT chk_graph_index_job_manifest
        CHECK (
            manifest_fingerprint IS NULL
            OR manifest_fingerprint ~ '^[0-9a-f]{64}$'
        ),
    ADD CONSTRAINT chk_graph_index_job_cancellation
        CHECK (
            (
                cancellation_requested
                AND cancellation_requested_at IS NOT NULL
            )
            OR (
                NOT cancellation_requested
                AND cancellation_requested_at IS NULL
            )
        );

ALTER TABLE graph_projection_heads
    ADD COLUMN idempotency_key varchar(255),
    ADD COLUMN manifest_fingerprint varchar(64),
    ADD CONSTRAINT chk_graph_projection_manifest
        CHECK (
            (
                idempotency_key IS NULL
                AND manifest_fingerprint IS NULL
            )
            OR (
                idempotency_key IS NOT NULL
                AND manifest_fingerprint ~ '^[0-9a-f]{64}$'
            )
        );

CREATE UNIQUE INDEX uq_graph_projection_idempotency
    ON graph_projection_heads (organization_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE TABLE graph_model_invocation_cache (
    organization_id uuid NOT NULL REFERENCES organizations(id),
    workspace varchar(255) NOT NULL,
    collection_name varchar(255) NOT NULL,
    operation varchar(64) NOT NULL,
    input_hash varchar(64) NOT NULL,
    model_route_fingerprint varchar(255) NOT NULL,
    profile_fingerprint varchar(255) NOT NULL,
    media_type varchar(255) NOT NULL,
    payload text NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    PRIMARY KEY (
        organization_id,
        workspace,
        collection_name,
        operation,
        input_hash,
        model_route_fingerprint,
        profile_fingerprint
    ),
    CONSTRAINT chk_graph_model_cache_hash
        CHECK (input_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_graph_model_cache_expiry
        CHECK (expires_at > created_at)
);

CREATE INDEX idx_graph_model_cache_expiry
    ON graph_model_invocation_cache (expires_at);

CREATE TABLE graph_retrieval_result_cache (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    workspace varchar(255) NOT NULL,
    collection_name varchar(255) NOT NULL,
    publication_batch_id uuid NOT NULL,
    publication_generation bigint NOT NULL,
    publication_manifest_fingerprint varchar(255) NOT NULL,
    publication_kinds varchar(255) NOT NULL,
    authorization_fingerprint varchar(64) NOT NULL,
    query_hash varchar(64) NOT NULL,
    strategy varchar(64) NOT NULL,
    model_route_fingerprint varchar(255) NOT NULL,
    media_type varchar(255) NOT NULL,
    payload text NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    UNIQUE (
        organization_id,
        workspace,
        collection_name,
        publication_batch_id,
        publication_generation,
        publication_manifest_fingerprint,
        publication_kinds,
        authorization_fingerprint,
        query_hash,
        strategy,
        model_route_fingerprint
    ),
    CONSTRAINT chk_graph_retrieval_cache_generation
        CHECK (publication_generation >= 0),
    CONSTRAINT chk_graph_retrieval_cache_authorization
        CHECK (authorization_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_graph_retrieval_cache_query
        CHECK (query_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_graph_retrieval_cache_expiry
        CHECK (expires_at > created_at)
);

CREATE INDEX idx_graph_retrieval_cache_expiry
    ON graph_retrieval_result_cache (expires_at);

CREATE TABLE graph_retrieval_cache_evidence (
    cache_entry_id uuid NOT NULL
        REFERENCES graph_retrieval_result_cache(id) ON DELETE CASCADE,
    ordinal integer NOT NULL,
    organization_id uuid NOT NULL,
    knowledge_asset_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    chunk_id uuid,
    acl_snapshot_id uuid NOT NULL,
    acl_generation bigint NOT NULL,
    PRIMARY KEY (cache_entry_id, ordinal),
    CONSTRAINT fk_graph_cache_evidence_asset
        FOREIGN KEY (knowledge_asset_id, organization_id)
        REFERENCES knowledge_assets(id, organization_id),
    CONSTRAINT fk_graph_cache_evidence_revision
        FOREIGN KEY (
            source_revision_id,
            organization_id,
            knowledge_asset_id
        )
        REFERENCES source_revisions(
            id,
            organization_id,
            knowledge_asset_id
        ),
    CONSTRAINT fk_graph_cache_evidence_acl
        FOREIGN KEY (acl_snapshot_id, organization_id, acl_generation)
        REFERENCES source_acl_snapshots(id, organization_id, acl_generation),
    CONSTRAINT chk_graph_cache_evidence_ordinal CHECK (ordinal >= 0),
    CONSTRAINT chk_graph_cache_evidence_acl_generation
        CHECK (acl_generation >= 0)
);

CREATE TABLE graph_curation_records (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    workspace varchar(255) NOT NULL,
    collection_name varchar(255) NOT NULL,
    curation_kind varchar(32) NOT NULL,
    identity_kind varchar(16),
    identity_id uuid,
    target_identity_id uuid,
    source_entity_id uuid,
    target_entity_id uuid,
    identity_name text,
    contribution_type varchar(255),
    keywords text,
    description text,
    weight double precision,
    governing_knowledge_asset_id uuid,
    governing_source_revision_id uuid,
    governing_chunk_id uuid,
    governing_acl_snapshot_id uuid,
    governing_acl_generation bigint,
    actor_user_id uuid NOT NULL,
    authorization_model_id varchar(255) NOT NULL,
    curation_acl_generation bigint NOT NULL,
    curated_at timestamptz NOT NULL,
    reason text NOT NULL,
    idempotency_key varchar(255) NOT NULL,
    content_fingerprint varchar(64) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    deactivated_by_user_id uuid,
    deactivated_at timestamptz,
    deactivation_reason text,
    UNIQUE (
        organization_id,
        workspace,
        collection_name,
        idempotency_key
    ),
    CONSTRAINT fk_graph_curation_governing_asset
        FOREIGN KEY (governing_knowledge_asset_id, organization_id)
        REFERENCES knowledge_assets(id, organization_id),
    CONSTRAINT fk_graph_curation_governing_revision
        FOREIGN KEY (
            governing_source_revision_id,
            organization_id,
            governing_knowledge_asset_id
        )
        REFERENCES source_revisions(
            id,
            organization_id,
            knowledge_asset_id
        ),
    CONSTRAINT fk_graph_curation_governing_acl
        FOREIGN KEY (
            governing_acl_snapshot_id,
            organization_id,
            governing_acl_generation
        )
        REFERENCES source_acl_snapshots(id, organization_id, acl_generation),
    CONSTRAINT chk_graph_curation_kind
        CHECK (curation_kind IN (
            'CURATED_ENTITY',
            'CURATED_RELATION',
            'IDENTITY_ALIAS',
            'IDENTITY_SUPPRESSION'
        )),
    CONSTRAINT chk_graph_curation_identity_kind
        CHECK (identity_kind IS NULL OR identity_kind IN ('ENTITY', 'RELATION')),
    CONSTRAINT chk_graph_curation_fingerprint
        CHECK (content_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_graph_curation_acl_generation
        CHECK (curation_acl_generation >= 0),
    CONSTRAINT chk_graph_curation_weight
        CHECK (weight IS NULL OR weight > 0),
    CONSTRAINT chk_graph_curation_deactivation
        CHECK (
            (
                active
                AND deactivated_by_user_id IS NULL
                AND deactivated_at IS NULL
                AND deactivation_reason IS NULL
            )
            OR (
                NOT active
                AND deactivated_by_user_id IS NOT NULL
                AND deactivated_at IS NOT NULL
                AND deactivation_reason IS NOT NULL
            )
        )
);

CREATE INDEX idx_graph_curation_active_namespace
    ON graph_curation_records (
        organization_id,
        workspace,
        collection_name,
        curated_at,
        id
    )
    WHERE active;
