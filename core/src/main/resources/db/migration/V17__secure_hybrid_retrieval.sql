ALTER TABLE knowledge_chunks
    ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        to_tsvector(
            'simple',
            coalesce(heading, '') || ' ' || content
        )
    ) STORED;

CREATE INDEX idx_knowledge_chunk_search_vector_active
    ON knowledge_chunks USING gin (search_vector)
    WHERE active;

ALTER TABLE permission_audit_events
    ADD COLUMN authorization_model_id varchar(255),
    ADD COLUMN source_revision_id uuid,
    ADD COLUMN knowledge_chunk_id uuid,
    ADD COLUMN embedding_profile_id uuid,
    ADD COLUMN projection_generation bigint;

ALTER TABLE permission_audit_events
    ADD CONSTRAINT chk_permission_audit_projection_generation
        CHECK (projection_generation IS NULL OR projection_generation > 0);

CREATE INDEX idx_permission_audit_chunk
    ON permission_audit_events (organization_id, knowledge_chunk_id)
    WHERE knowledge_chunk_id IS NOT NULL;
