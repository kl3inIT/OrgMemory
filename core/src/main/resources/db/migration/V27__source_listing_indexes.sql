CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_source_object_title_trgm
    ON source_objects USING gin (title gin_trgm_ops);

CREATE INDEX idx_source_revision_file_name_trgm
    ON source_revisions USING gin (file_name gin_trgm_ops);

CREATE INDEX idx_source_object_organization_classification
    ON source_objects (organization_id, classification);

CREATE INDEX idx_source_revision_listing_cursor
    ON source_revisions (organization_id, updated_at DESC, id);
