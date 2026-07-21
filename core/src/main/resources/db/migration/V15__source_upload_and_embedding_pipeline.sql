CREATE TABLE source_objects (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    department_id uuid,
    created_by_user_id uuid NOT NULL REFERENCES app_users(id),
    source_type varchar(32) NOT NULL,
    source_connection_key varchar(128) NOT NULL,
    external_object_id varchar(512) NOT NULL,
    title varchar(255) NOT NULL,
    classification varchar(32) NOT NULL,
    declared_access varchar(32) NOT NULL,
    current_revision_id uuid,
    status varchar(32) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_source_object_identity UNIQUE (
        organization_id,
        source_type,
        source_connection_key,
        external_object_id
    ),
    CONSTRAINT uq_source_object_id_organization UNIQUE (id, organization_id),
    CONSTRAINT fk_source_object_department_organization
        FOREIGN KEY (department_id, organization_id)
        REFERENCES departments(id, organization_id),
    CONSTRAINT chk_source_object_nonblank CHECK (
        btrim(source_connection_key) <> ''
        AND btrim(external_object_id) <> ''
        AND btrim(title) <> ''
    ),
    CONSTRAINT chk_source_object_type CHECK (source_type IN ('UPLOAD', 'SLACK')),
    CONSTRAINT chk_source_object_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_source_object_classification_access CHECK (
        (classification = 'PUBLIC' AND declared_access = 'ALL')
        OR (classification = 'INTERNAL' AND declared_access = 'ALL_EMPLOYEES')
        OR (classification = 'CONFIDENTIAL' AND declared_access = 'OWN_DEPARTMENT')
        OR (classification = 'RESTRICTED' AND declared_access = 'EXECUTIVE_ONLY')
    ),
    CONSTRAINT chk_source_object_confidential_department CHECK (
        classification <> 'CONFIDENTIAL' OR department_id IS NOT NULL
    )
);

CREATE TABLE evidence_blobs (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    object_key varchar(1024) NOT NULL,
    media_type varchar(255) NOT NULL,
    content_length bigint NOT NULL,
    content_sha256 varchar(64) NOT NULL,
    etag varchar(255),
    storage_version varchar(255),
    scan_status varchar(32) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_evidence_blob_object_key UNIQUE (object_key),
    CONSTRAINT uq_evidence_blob_id_organization UNIQUE (id, organization_id),
    CONSTRAINT chk_evidence_blob_nonblank CHECK (
        btrim(object_key) <> '' AND btrim(media_type) <> ''
    ),
    CONSTRAINT chk_evidence_blob_length CHECK (content_length > 0),
    CONSTRAINT chk_evidence_blob_sha CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_evidence_blob_scan_status
        CHECK (scan_status IN ('PENDING', 'BASIC_VALIDATED', 'REJECTED'))
);

CREATE TABLE embedding_profiles (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    profile_key varchar(255) NOT NULL,
    provider varchar(64) NOT NULL,
    model varchar(128) NOT NULL,
    dimensions integer NOT NULL,
    distance_metric varchar(32) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_embedding_profile_key UNIQUE (organization_id, profile_key),
    CONSTRAINT uq_embedding_profile_projection UNIQUE (id, organization_id, dimensions),
    CONSTRAINT chk_embedding_profile_nonblank CHECK (
        btrim(profile_key) <> ''
        AND btrim(provider) <> ''
        AND btrim(model) <> ''
    ),
    CONSTRAINT chk_embedding_profile_dimensions CHECK (
        dimensions > 0 AND dimensions <= 16000
    ),
    CONSTRAINT chk_embedding_profile_distance_metric CHECK (
        distance_metric IN ('COSINE')
    )
);

CREATE TABLE source_revisions (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    source_object_id uuid NOT NULL,
    evidence_blob_id uuid NOT NULL,
    revision_number bigint NOT NULL,
    file_name varchar(255) NOT NULL,
    media_type varchar(255) NOT NULL,
    content_length bigint NOT NULL,
    content_sha256 varchar(64) NOT NULL,
    classification varchar(32) NOT NULL,
    declared_access varchar(32) NOT NULL,
    department_id uuid,
    created_by_user_id uuid NOT NULL REFERENCES app_users(id),
    status varchar(32) NOT NULL,
    failure_code varchar(64),
    failure_message varchar(512),
    pipeline_version varchar(64),
    parser_version varchar(64),
    chunker_version varchar(64),
    embedding_profile_id uuid,
    embedding_dimensions integer,
    raw_source_object_id uuid REFERENCES raw_source_objects(id),
    normalized_record_id uuid REFERENCES normalized_records(id),
    knowledge_asset_id uuid REFERENCES knowledge_assets(id),
    processed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_source_revision_number UNIQUE (source_object_id, revision_number),
    CONSTRAINT uq_source_revision_content UNIQUE (source_object_id, content_sha256),
    CONSTRAINT uq_source_revision_id_organization UNIQUE (id, organization_id),
    CONSTRAINT uq_source_revision_chain UNIQUE (id, organization_id, source_object_id),
    CONSTRAINT fk_source_revision_object_organization
        FOREIGN KEY (source_object_id, organization_id)
        REFERENCES source_objects(id, organization_id),
    CONSTRAINT fk_source_revision_blob_organization
        FOREIGN KEY (evidence_blob_id, organization_id)
        REFERENCES evidence_blobs(id, organization_id),
    CONSTRAINT fk_source_revision_department_organization
        FOREIGN KEY (department_id, organization_id)
        REFERENCES departments(id, organization_id),
    CONSTRAINT fk_source_revision_embedding_profile
        FOREIGN KEY (embedding_profile_id, organization_id, embedding_dimensions)
        REFERENCES embedding_profiles(id, organization_id, dimensions),
    CONSTRAINT chk_source_revision_nonblank CHECK (
        btrim(file_name) <> '' AND btrim(media_type) <> ''
    ),
    CONSTRAINT chk_source_revision_number CHECK (revision_number > 0),
    CONSTRAINT chk_source_revision_length CHECK (content_length > 0),
    CONSTRAINT chk_source_revision_sha CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_source_revision_status CHECK (status IN (
        'RECEIVED', 'VALIDATING', 'PARSING', 'CHUNKING', 'EMBEDDING',
        'PUBLISHING', 'READY', 'QUARANTINED', 'FAILED'
    )),
    CONSTRAINT chk_source_revision_embedding_dimensions CHECK (
        embedding_dimensions IS NULL OR embedding_dimensions > 0
    ),
    CONSTRAINT chk_source_revision_ready CHECK (
        status <> 'READY'
        OR (
            pipeline_version IS NOT NULL
            AND parser_version IS NOT NULL
            AND chunker_version IS NOT NULL
            AND embedding_profile_id IS NOT NULL
            AND embedding_dimensions IS NOT NULL
            AND knowledge_asset_id IS NOT NULL
            AND processed_at IS NOT NULL
            AND failure_code IS NULL
        )
    )
);

ALTER TABLE source_objects
    ADD CONSTRAINT fk_source_object_current_revision
    FOREIGN KEY (current_revision_id, organization_id, id)
    REFERENCES source_revisions(id, organization_id, source_object_id);

CREATE TABLE source_ingestion_jobs (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    source_revision_id uuid NOT NULL,
    job_type varchar(64) NOT NULL,
    status varchar(32) NOT NULL,
    available_at timestamptz NOT NULL,
    lease_owner varchar(128),
    lease_until timestamptz,
    attempt_count integer NOT NULL,
    max_attempts integer NOT NULL,
    last_error_code varchar(64),
    last_error_message varchar(512),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_source_ingestion_job UNIQUE (source_revision_id, job_type),
    CONSTRAINT fk_source_ingestion_job_revision_organization
        FOREIGN KEY (source_revision_id, organization_id)
        REFERENCES source_revisions(id, organization_id),
    CONSTRAINT chk_source_ingestion_job_type
        CHECK (job_type = 'PROCESS_SOURCE_REVISION'),
    CONSTRAINT chk_source_ingestion_job_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_source_ingestion_job_attempts CHECK (
        attempt_count >= 0 AND max_attempts > 0 AND attempt_count <= max_attempts
    ),
    CONSTRAINT chk_source_ingestion_job_lease CHECK (
        (status = 'PROCESSING' AND lease_owner IS NOT NULL AND lease_until IS NOT NULL)
        OR status <> 'PROCESSING'
    )
);

CREATE TABLE knowledge_chunks (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    source_object_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    knowledge_asset_id uuid NOT NULL,
    chunk_index integer NOT NULL,
    content text NOT NULL,
    content_sha256 varchar(64) NOT NULL,
    token_count integer,
    start_page integer,
    end_page integer,
    heading varchar(512),
    embedding vector NOT NULL,
    embedding_profile_id uuid NOT NULL,
    embedding_dimensions integer NOT NULL,
    pipeline_version varchar(64) NOT NULL,
    projection_generation bigint NOT NULL,
    active boolean NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_knowledge_chunk_revision_index UNIQUE (source_revision_id, chunk_index),
    CONSTRAINT fk_knowledge_chunk_source_organization
        FOREIGN KEY (source_object_id, organization_id)
        REFERENCES source_objects(id, organization_id),
    CONSTRAINT fk_knowledge_chunk_revision_organization
        FOREIGN KEY (source_revision_id, organization_id)
        REFERENCES source_revisions(id, organization_id),
    CONSTRAINT fk_knowledge_chunk_asset_organization
        FOREIGN KEY (knowledge_asset_id, organization_id)
        REFERENCES knowledge_assets(id, organization_id),
    CONSTRAINT fk_knowledge_chunk_embedding_profile
        FOREIGN KEY (embedding_profile_id, organization_id, embedding_dimensions)
        REFERENCES embedding_profiles(id, organization_id, dimensions),
    CONSTRAINT chk_knowledge_chunk_content CHECK (btrim(content) <> ''),
    CONSTRAINT chk_knowledge_chunk_sha CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_knowledge_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT chk_knowledge_chunk_dimensions CHECK (
        embedding_dimensions > 0 AND embedding_dimensions <= 16000
    ),
    CONSTRAINT chk_knowledge_chunk_generation CHECK (projection_generation > 0)
);

CREATE INDEX idx_source_object_owner
    ON source_objects (organization_id, created_by_user_id, updated_at DESC);
CREATE INDEX idx_source_revision_status
    ON source_revisions (organization_id, status, updated_at DESC);
CREATE INDEX idx_source_ingestion_claim
    ON source_ingestion_jobs (status, available_at, created_at)
    WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX idx_knowledge_chunk_revision
    ON knowledge_chunks (organization_id, source_revision_id, active);
CREATE INDEX idx_knowledge_chunk_embedding_1536_hnsw
    ON knowledge_chunks USING hnsw ((embedding::vector(1536)) vector_cosine_ops)
    WHERE active AND embedding_dimensions = 1536;
