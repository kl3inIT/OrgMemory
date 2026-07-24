CREATE TABLE projection_batches (
    batch_id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    workspace varchar(255) NOT NULL,
    collection_name varchar(255) NOT NULL,
    expected_previous_generation bigint NOT NULL,
    generation bigint NOT NULL,
    idempotency_key varchar(255) NOT NULL,
    manifest_fingerprint varchar(255) NOT NULL,
    required_projections text NOT NULL,
    status varchar(16) NOT NULL,
    created_at timestamptz NOT NULL,
    published_at timestamptz,
    aborted_at timestamptz,
    abort_reason text,
    UNIQUE (organization_id, workspace, collection_name, idempotency_key),
    CONSTRAINT chk_projection_batch_generation
        CHECK (
            expected_previous_generation >= 0
            AND generation = expected_previous_generation + 1
        ),
    CONSTRAINT chk_projection_batch_status
        CHECK (status IN ('PREPARING', 'PUBLISHED', 'ABORTED')),
    CONSTRAINT chk_projection_batch_completion
        CHECK (
            (
                status = 'PREPARING'
                AND published_at IS NULL
                AND aborted_at IS NULL
                AND abort_reason IS NULL
            )
            OR (
                status = 'PUBLISHED'
                AND published_at IS NOT NULL
                AND aborted_at IS NULL
                AND abort_reason IS NULL
            )
            OR (
                status = 'ABORTED'
                AND published_at IS NULL
                AND aborted_at IS NOT NULL
                AND btrim(abort_reason) <> ''
            )
        )
);

CREATE TABLE projection_batch_receipts (
    batch_id uuid NOT NULL
        REFERENCES projection_batches(batch_id) ON DELETE CASCADE,
    projection_kind varchar(32) NOT NULL,
    prepared_at timestamptz NOT NULL,
    PRIMARY KEY (batch_id, projection_kind),
    CONSTRAINT chk_projection_receipt_kind
        CHECK (projection_kind IN ('CONTENT', 'LEXICAL', 'VECTOR', 'GRAPH'))
);

CREATE TABLE projection_publications (
    batch_id uuid PRIMARY KEY
        REFERENCES projection_batches(batch_id),
    organization_id uuid NOT NULL,
    workspace varchar(255) NOT NULL,
    collection_name varchar(255) NOT NULL,
    generation bigint NOT NULL,
    manifest_fingerprint varchar(255) NOT NULL,
    projections text NOT NULL,
    published_at timestamptz NOT NULL,
    UNIQUE (organization_id, workspace, collection_name, generation)
);

CREATE TABLE projection_namespace_heads (
    organization_id uuid NOT NULL,
    workspace varchar(255) NOT NULL,
    collection_name varchar(255) NOT NULL,
    batch_id uuid NOT NULL UNIQUE
        REFERENCES projection_publications(batch_id),
    generation bigint NOT NULL,
    PRIMARY KEY (organization_id, workspace, collection_name),
    CONSTRAINT chk_projection_head_generation CHECK (generation > 0)
);

CREATE TABLE projection_stage_initializations (
    batch_id uuid NOT NULL
        REFERENCES projection_batches(batch_id) ON DELETE CASCADE,
    projection_kind varchar(32) NOT NULL,
    initialized_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (batch_id, projection_kind),
    CONSTRAINT chk_projection_initialization_kind
        CHECK (projection_kind IN ('CONTENT', 'LEXICAL', 'VECTOR', 'GRAPH'))
);

CREATE TABLE projection_content_records (
    batch_id uuid NOT NULL
        REFERENCES projection_batches(batch_id) ON DELETE CASCADE,
    record_id varchar(255) NOT NULL,
    organization_id uuid NOT NULL,
    knowledge_asset_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    chunk_id uuid,
    acl_snapshot_id uuid NOT NULL,
    acl_generation bigint NOT NULL,
    content_kind varchar(64) NOT NULL,
    content text NOT NULL,
    token_count integer NOT NULL,
    metadata text NOT NULL,
    PRIMARY KEY (batch_id, record_id),
    CONSTRAINT chk_projection_content_acl_generation CHECK (acl_generation >= 0),
    CONSTRAINT chk_projection_content_token_count CHECK (token_count >= 0)
);

CREATE INDEX idx_projection_content_visible
    ON projection_content_records (
        batch_id,
        organization_id,
        knowledge_asset_id,
        record_id
    );

CREATE TABLE projection_lexical_documents (
    batch_id uuid NOT NULL
        REFERENCES projection_batches(batch_id) ON DELETE CASCADE,
    document_id varchar(255) NOT NULL,
    organization_id uuid NOT NULL,
    knowledge_asset_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    chunk_id uuid,
    acl_snapshot_id uuid NOT NULL,
    acl_generation bigint NOT NULL,
    content text NOT NULL,
    fields text NOT NULL,
    search_vector tsvector GENERATED ALWAYS AS (
        to_tsvector('simple', content || ' ' || fields)
    ) STORED,
    PRIMARY KEY (batch_id, document_id),
    CONSTRAINT chk_projection_lexical_acl_generation CHECK (acl_generation >= 0)
);

CREATE INDEX idx_projection_lexical_search
    ON projection_lexical_documents USING gin (search_vector);

CREATE INDEX idx_projection_lexical_visible
    ON projection_lexical_documents (
        batch_id,
        organization_id,
        knowledge_asset_id,
        document_id
    );

CREATE TABLE projection_vector_records (
    batch_id uuid NOT NULL
        REFERENCES projection_batches(batch_id) ON DELETE CASCADE,
    record_id varchar(255) NOT NULL,
    subject_id varchar(255) NOT NULL,
    organization_id uuid NOT NULL,
    knowledge_asset_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    chunk_id uuid,
    acl_snapshot_id uuid NOT NULL,
    acl_generation bigint NOT NULL,
    vector_kind varchar(32) NOT NULL,
    embedding_profile_id uuid NOT NULL,
    model varchar(255) NOT NULL,
    dimensions integer NOT NULL,
    embedding vector NOT NULL,
    metadata text NOT NULL,
    PRIMARY KEY (batch_id, record_id),
    CONSTRAINT chk_projection_vector_kind
        CHECK (vector_kind IN ('CHUNK', 'ENTITY', 'RELATION')),
    CONSTRAINT chk_projection_vector_dimensions CHECK (dimensions > 0),
    CONSTRAINT chk_projection_vector_acl_generation CHECK (acl_generation >= 0)
);

CREATE INDEX idx_projection_vector_visible
    ON projection_vector_records (
        batch_id,
        organization_id,
        knowledge_asset_id,
        embedding_profile_id,
        vector_kind
    );

CREATE INDEX idx_projection_vector_subject
    ON projection_vector_records (batch_id, subject_id);

CREATE TABLE projection_graph_entities (
    batch_id uuid NOT NULL
        REFERENCES projection_batches(batch_id) ON DELETE CASCADE,
    entity_id uuid NOT NULL,
    normalized_name text NOT NULL,
    PRIMARY KEY (batch_id, entity_id)
);

CREATE TABLE projection_graph_relations (
    batch_id uuid NOT NULL
        REFERENCES projection_batches(batch_id) ON DELETE CASCADE,
    relation_id uuid NOT NULL,
    source_entity_id uuid NOT NULL,
    target_entity_id uuid NOT NULL,
    orientation varchar(16) NOT NULL,
    PRIMARY KEY (batch_id, relation_id),
    CONSTRAINT chk_projection_graph_orientation
        CHECK (orientation IN ('DIRECTED', 'UNDIRECTED')),
    CONSTRAINT chk_projection_graph_endpoints
        CHECK (source_entity_id <> target_entity_id)
);

CREATE INDEX idx_projection_graph_relation_source
    ON projection_graph_relations (batch_id, source_entity_id);

CREATE INDEX idx_projection_graph_relation_target
    ON projection_graph_relations (batch_id, target_entity_id);

CREATE TABLE projection_graph_entity_contributions (
    batch_id uuid NOT NULL
        REFERENCES projection_batches(batch_id) ON DELETE CASCADE,
    contribution_id uuid NOT NULL,
    entity_id uuid NOT NULL,
    entity_type varchar(255) NOT NULL,
    description text NOT NULL,
    organization_id uuid NOT NULL,
    knowledge_asset_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    chunk_id uuid,
    acl_snapshot_id uuid NOT NULL,
    acl_generation bigint NOT NULL,
    projection_generation bigint NOT NULL,
    extractor_provider varchar(255) NOT NULL,
    extractor_model varchar(255) NOT NULL,
    prompt_version varchar(255) NOT NULL,
    extraction_profile_fingerprint varchar(64) NOT NULL,
    confidence double precision NOT NULL,
    extracted_at timestamptz NOT NULL,
    PRIMARY KEY (batch_id, contribution_id),
    CONSTRAINT fk_projection_graph_entity_identity
        FOREIGN KEY (batch_id, entity_id)
        REFERENCES projection_graph_entities(batch_id, entity_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_projection_graph_entity_acl CHECK (acl_generation >= 0),
    CONSTRAINT chk_projection_graph_entity_generation CHECK (projection_generation >= 0),
    CONSTRAINT chk_projection_graph_entity_confidence
        CHECK (confidence >= 0.0 AND confidence <= 1.0)
);

CREATE INDEX idx_projection_graph_entity_visible
    ON projection_graph_entity_contributions (
        batch_id,
        organization_id,
        knowledge_asset_id,
        entity_id
    );

CREATE INDEX idx_projection_graph_entity_revision
    ON projection_graph_entity_contributions (batch_id, source_revision_id);

CREATE TABLE projection_graph_relation_contributions (
    batch_id uuid NOT NULL
        REFERENCES projection_batches(batch_id) ON DELETE CASCADE,
    contribution_id uuid NOT NULL,
    relation_id uuid NOT NULL,
    relation_type varchar(255) NOT NULL,
    keywords text NOT NULL,
    description text NOT NULL,
    weight double precision NOT NULL,
    organization_id uuid NOT NULL,
    knowledge_asset_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    chunk_id uuid,
    acl_snapshot_id uuid NOT NULL,
    acl_generation bigint NOT NULL,
    projection_generation bigint NOT NULL,
    extractor_provider varchar(255) NOT NULL,
    extractor_model varchar(255) NOT NULL,
    prompt_version varchar(255) NOT NULL,
    extraction_profile_fingerprint varchar(64) NOT NULL,
    confidence double precision NOT NULL,
    extracted_at timestamptz NOT NULL,
    PRIMARY KEY (batch_id, contribution_id),
    CONSTRAINT fk_projection_graph_relation_identity
        FOREIGN KEY (batch_id, relation_id)
        REFERENCES projection_graph_relations(batch_id, relation_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_projection_graph_relation_acl CHECK (acl_generation >= 0),
    CONSTRAINT chk_projection_graph_relation_generation CHECK (projection_generation >= 0),
    CONSTRAINT chk_projection_graph_relation_weight CHECK (weight > 0.0),
    CONSTRAINT chk_projection_graph_relation_confidence
        CHECK (confidence >= 0.0 AND confidence <= 1.0)
);

CREATE INDEX idx_projection_graph_relation_visible
    ON projection_graph_relation_contributions (
        batch_id,
        organization_id,
        knowledge_asset_id,
        relation_id
    );

CREATE INDEX idx_projection_graph_relation_revision
    ON projection_graph_relation_contributions (batch_id, source_revision_id);
