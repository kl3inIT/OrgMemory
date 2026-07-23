-- Secure GraphRAG stores canonical identity separately from evidence-level
-- contributions. Only contributions matching a published revision head are visible.

-- AGE is an optional topology accelerator. The OrgMemory PostgreSQL image provides
-- it, while pgvector-only test/development databases can still run the canonical
-- relational projection. Runtime configuration fails fast when AGE is required but
-- unavailable.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_available_extensions
        WHERE name = 'age'
    ) THEN
        EXECUTE 'CREATE EXTENSION IF NOT EXISTS age CASCADE';
    END IF;
END;
$$;

ALTER TABLE source_revisions
    ADD CONSTRAINT uq_source_revision_graph_asset
        UNIQUE (id, organization_id, knowledge_asset_id);

ALTER TABLE source_acl_snapshots
    ADD CONSTRAINT uq_source_acl_graph_generation
        UNIQUE (id, organization_id, acl_generation);

ALTER TABLE knowledge_assets
    ADD CONSTRAINT uq_knowledge_asset_graph_acl
        UNIQUE (id, organization_id, source_acl_snapshot_id);

ALTER TABLE knowledge_chunks
    ADD CONSTRAINT uq_knowledge_chunk_graph_provenance
        UNIQUE (
            id,
            organization_id,
            source_revision_id,
            knowledge_asset_id,
            projection_generation
        );

CREATE TABLE graph_entities (
    organization_id uuid NOT NULL REFERENCES organizations(id),
    id uuid NOT NULL,
    normalized_name varchar(512) NOT NULL,
    entity_type varchar(128) NOT NULL,
    search_vector tsvector GENERATED ALWAYS AS (
        to_tsvector(
            'simple',
            coalesce(normalized_name, '') || ' ' || coalesce(entity_type, '')
        )
    ) STORED,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (organization_id, id),
    CONSTRAINT chk_graph_entity_nonblank CHECK (
        btrim(normalized_name) <> '' AND btrim(entity_type) <> ''
    )
);

CREATE TABLE graph_relations (
    organization_id uuid NOT NULL REFERENCES organizations(id),
    id uuid NOT NULL,
    source_entity_id uuid NOT NULL,
    target_entity_id uuid NOT NULL,
    relation_type varchar(128) NOT NULL,
    orientation varchar(16) NOT NULL,
    search_vector tsvector GENERATED ALWAYS AS (
        to_tsvector('simple', coalesce(relation_type, ''))
    ) STORED,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (organization_id, id),
    CONSTRAINT fk_graph_relation_source
        FOREIGN KEY (organization_id, source_entity_id)
        REFERENCES graph_entities(organization_id, id),
    CONSTRAINT fk_graph_relation_target
        FOREIGN KEY (organization_id, target_entity_id)
        REFERENCES graph_entities(organization_id, id),
    CONSTRAINT chk_graph_relation_nonblank CHECK (btrim(relation_type) <> ''),
    CONSTRAINT chk_graph_relation_distinct_endpoints
        CHECK (source_entity_id <> target_entity_id),
    CONSTRAINT chk_graph_relation_orientation
        CHECK (orientation IN ('DIRECTED', 'UNDIRECTED')),
    CONSTRAINT chk_graph_relation_undirected_order CHECK (
        orientation <> 'UNDIRECTED' OR source_entity_id < target_entity_id
    )
);

CREATE TABLE graph_projection_heads (
    organization_id uuid NOT NULL REFERENCES organizations(id),
    source_revision_id uuid NOT NULL,
    knowledge_asset_id uuid NOT NULL,
    projection_generation bigint NOT NULL,
    published_at timestamptz NOT NULL,
    PRIMARY KEY (organization_id, source_revision_id),
    CONSTRAINT uq_graph_projection_published_generation
        UNIQUE (
            organization_id,
            source_revision_id,
            knowledge_asset_id,
            projection_generation
        ),
    CONSTRAINT fk_graph_projection_revision_asset
        FOREIGN KEY (source_revision_id, organization_id, knowledge_asset_id)
        REFERENCES source_revisions(id, organization_id, knowledge_asset_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_graph_projection_generation
        CHECK (projection_generation >= 0)
);

CREATE TABLE graph_entity_contributions (
    organization_id uuid NOT NULL,
    id uuid NOT NULL,
    entity_id uuid NOT NULL,
    knowledge_asset_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    chunk_id uuid NOT NULL,
    acl_snapshot_id uuid NOT NULL,
    acl_generation bigint NOT NULL,
    projection_generation bigint NOT NULL,
    description text NOT NULL,
    extractor_provider varchar(128) NOT NULL,
    extractor_model varchar(255) NOT NULL,
    prompt_version varchar(128) NOT NULL,
    confidence double precision NOT NULL,
    extracted_at timestamptz NOT NULL,
    search_vector tsvector GENERATED ALWAYS AS (
        to_tsvector('simple', coalesce(description, ''))
    ) STORED,
    PRIMARY KEY (organization_id, id),
    CONSTRAINT uq_graph_entity_contribution_generation
        UNIQUE (
            organization_id,
            id,
            source_revision_id,
            knowledge_asset_id,
            projection_generation
        ),
    CONSTRAINT fk_graph_entity_contribution_entity
        FOREIGN KEY (organization_id, entity_id)
        REFERENCES graph_entities(organization_id, id),
    CONSTRAINT fk_graph_entity_contribution_head
        FOREIGN KEY (
            organization_id,
            source_revision_id,
            knowledge_asset_id,
            projection_generation
        )
        REFERENCES graph_projection_heads (
            organization_id,
            source_revision_id,
            knowledge_asset_id,
            projection_generation
        )
        ON DELETE CASCADE,
    CONSTRAINT fk_graph_entity_contribution_chunk
        FOREIGN KEY (
            chunk_id,
            organization_id,
            source_revision_id,
            knowledge_asset_id,
            projection_generation
        )
        REFERENCES knowledge_chunks (
            id,
            organization_id,
            source_revision_id,
            knowledge_asset_id,
            projection_generation
        ),
    CONSTRAINT fk_graph_entity_contribution_acl_generation
        FOREIGN KEY (acl_snapshot_id, organization_id, acl_generation)
        REFERENCES source_acl_snapshots(id, organization_id, acl_generation),
    CONSTRAINT fk_graph_entity_contribution_asset_acl
        FOREIGN KEY (knowledge_asset_id, organization_id, acl_snapshot_id)
        REFERENCES knowledge_assets(id, organization_id, source_acl_snapshot_id),
    CONSTRAINT chk_graph_entity_contribution_description
        CHECK (btrim(description) <> ''),
    CONSTRAINT chk_graph_entity_contribution_extractor CHECK (
        btrim(extractor_provider) <> ''
        AND btrim(extractor_model) <> ''
        AND btrim(prompt_version) <> ''
    ),
    CONSTRAINT chk_graph_entity_contribution_confidence
        CHECK (confidence >= 0.0 AND confidence <= 1.0)
);

CREATE TABLE graph_relation_contributions (
    organization_id uuid NOT NULL,
    id uuid NOT NULL,
    relation_id uuid NOT NULL,
    knowledge_asset_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    chunk_id uuid NOT NULL,
    acl_snapshot_id uuid NOT NULL,
    acl_generation bigint NOT NULL,
    projection_generation bigint NOT NULL,
    keywords varchar(255)[] NOT NULL,
    description text NOT NULL,
    search_content text NOT NULL,
    extractor_provider varchar(128) NOT NULL,
    extractor_model varchar(255) NOT NULL,
    prompt_version varchar(128) NOT NULL,
    confidence double precision NOT NULL,
    extracted_at timestamptz NOT NULL,
    search_vector tsvector GENERATED ALWAYS AS (
        to_tsvector('simple', coalesce(search_content, ''))
    ) STORED,
    PRIMARY KEY (organization_id, id),
    CONSTRAINT uq_graph_relation_contribution_generation
        UNIQUE (
            organization_id,
            id,
            source_revision_id,
            knowledge_asset_id,
            projection_generation
        ),
    CONSTRAINT fk_graph_relation_contribution_relation
        FOREIGN KEY (organization_id, relation_id)
        REFERENCES graph_relations(organization_id, id),
    CONSTRAINT fk_graph_relation_contribution_head
        FOREIGN KEY (
            organization_id,
            source_revision_id,
            knowledge_asset_id,
            projection_generation
        )
        REFERENCES graph_projection_heads (
            organization_id,
            source_revision_id,
            knowledge_asset_id,
            projection_generation
        )
        ON DELETE CASCADE,
    CONSTRAINT fk_graph_relation_contribution_chunk
        FOREIGN KEY (
            chunk_id,
            organization_id,
            source_revision_id,
            knowledge_asset_id,
            projection_generation
        )
        REFERENCES knowledge_chunks (
            id,
            organization_id,
            source_revision_id,
            knowledge_asset_id,
            projection_generation
        ),
    CONSTRAINT fk_graph_relation_contribution_acl_generation
        FOREIGN KEY (acl_snapshot_id, organization_id, acl_generation)
        REFERENCES source_acl_snapshots(id, organization_id, acl_generation),
    CONSTRAINT fk_graph_relation_contribution_asset_acl
        FOREIGN KEY (knowledge_asset_id, organization_id, acl_snapshot_id)
        REFERENCES knowledge_assets(id, organization_id, source_acl_snapshot_id),
    CONSTRAINT chk_graph_relation_contribution_keywords CHECK (
        array_position(keywords, NULL) IS NULL
    ),
    CONSTRAINT chk_graph_relation_contribution_description
        CHECK (btrim(description) <> '' AND btrim(search_content) <> ''),
    CONSTRAINT chk_graph_relation_contribution_extractor CHECK (
        btrim(extractor_provider) <> ''
        AND btrim(extractor_model) <> ''
        AND btrim(prompt_version) <> ''
    ),
    CONSTRAINT chk_graph_relation_contribution_confidence
        CHECK (confidence >= 0.0 AND confidence <= 1.0)
);

-- LightRAG keeps independent vector stores for chunks, entities and relations.
-- OrgMemory already owns chunk vectors in knowledge_chunks; these two tables add
-- contribution-level entity and relation vectors so permission filtering happens
-- before nearest-neighbour ranking.
CREATE TABLE graph_entity_embeddings (
    organization_id uuid NOT NULL,
    entity_contribution_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    knowledge_asset_id uuid NOT NULL,
    projection_generation bigint NOT NULL,
    embedding_profile_id uuid NOT NULL,
    embedding_dimensions integer NOT NULL,
    content_vector vector NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (
        organization_id,
        entity_contribution_id,
        embedding_profile_id
    ),
    CONSTRAINT fk_graph_entity_embedding_contribution
        FOREIGN KEY (
            organization_id,
            entity_contribution_id,
            source_revision_id,
            knowledge_asset_id,
            projection_generation
        )
        REFERENCES graph_entity_contributions(
            organization_id,
            id,
            source_revision_id,
            knowledge_asset_id,
            projection_generation
        )
        ON DELETE CASCADE,
    CONSTRAINT fk_graph_entity_embedding_profile
        FOREIGN KEY (
            embedding_profile_id,
            organization_id,
            embedding_dimensions
        )
        REFERENCES embedding_profiles(id, organization_id, dimensions),
    CONSTRAINT chk_graph_entity_embedding_dimensions CHECK (
        embedding_dimensions > 0
        AND embedding_dimensions <= 16000
        AND vector_dims(content_vector) = embedding_dimensions
    )
);

CREATE TABLE graph_relation_embeddings (
    organization_id uuid NOT NULL,
    relation_contribution_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    knowledge_asset_id uuid NOT NULL,
    projection_generation bigint NOT NULL,
    embedding_profile_id uuid NOT NULL,
    embedding_dimensions integer NOT NULL,
    content_vector vector NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (
        organization_id,
        relation_contribution_id,
        embedding_profile_id
    ),
    CONSTRAINT fk_graph_relation_embedding_contribution
        FOREIGN KEY (
            organization_id,
            relation_contribution_id,
            source_revision_id,
            knowledge_asset_id,
            projection_generation
        )
        REFERENCES graph_relation_contributions(
            organization_id,
            id,
            source_revision_id,
            knowledge_asset_id,
            projection_generation
        )
        ON DELETE CASCADE,
    CONSTRAINT fk_graph_relation_embedding_profile
        FOREIGN KEY (
            embedding_profile_id,
            organization_id,
            embedding_dimensions
        )
        REFERENCES embedding_profiles(id, organization_id, dimensions),
    CONSTRAINT chk_graph_relation_embedding_dimensions CHECK (
        embedding_dimensions > 0
        AND embedding_dimensions <= 16000
        AND vector_dims(content_vector) = embedding_dimensions
    )
);

CREATE INDEX idx_graph_entity_identity_search
    ON graph_entities USING gin (search_vector);
CREATE INDEX idx_graph_relation_identity_search
    ON graph_relations USING gin (search_vector);
CREATE INDEX idx_graph_entity_contribution_search
    ON graph_entity_contributions USING gin (search_vector);
CREATE INDEX idx_graph_relation_contribution_search
    ON graph_relation_contributions USING gin (search_vector);
CREATE INDEX idx_graph_entity_embedding_lookup
    ON graph_entity_embeddings (
        organization_id,
        embedding_profile_id,
        entity_contribution_id
    );
CREATE INDEX idx_graph_relation_embedding_lookup
    ON graph_relation_embeddings (
        organization_id,
        embedding_profile_id,
        relation_contribution_id
    );
CREATE INDEX idx_entity_embeddings_1536_hnsw_cosine
    ON graph_entity_embeddings
    USING hnsw ((content_vector::vector(1536)) vector_cosine_ops)
    WHERE embedding_dimensions = 1536;
CREATE INDEX idx_relation_embeddings_1536_hnsw_cosine
    ON graph_relation_embeddings
    USING hnsw ((content_vector::vector(1536)) vector_cosine_ops)
    WHERE embedding_dimensions = 1536;
CREATE INDEX idx_graph_entity_contribution_visibility
    ON graph_entity_contributions (
        organization_id,
        knowledge_asset_id,
        source_revision_id,
        projection_generation,
        entity_id
    );
CREATE INDEX idx_graph_relation_contribution_visibility
    ON graph_relation_contributions (
        organization_id,
        knowledge_asset_id,
        source_revision_id,
        projection_generation,
        relation_id
    );
CREATE INDEX idx_graph_relation_incident_source
    ON graph_relations (organization_id, source_entity_id, id);
CREATE INDEX idx_graph_relation_incident_target
    ON graph_relations (organization_id, target_entity_id, id);
