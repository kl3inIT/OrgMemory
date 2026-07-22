ALTER TABLE knowledge_assets
    ALTER COLUMN activated_at DROP NOT NULL;

ALTER TABLE knowledge_assets
    DROP CONSTRAINT chk_knowledge_status,
    DROP CONSTRAINT chk_knowledge_retired_at;

ALTER TABLE knowledge_assets
    ADD CONSTRAINT chk_knowledge_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'RETIRED')),
    ADD CONSTRAINT chk_knowledge_lifecycle CHECK (
        (status = 'PENDING' AND activated_at IS NULL AND retired_at IS NULL)
        OR (status = 'ACTIVE' AND activated_at IS NOT NULL AND retired_at IS NULL)
        OR (status = 'RETIRED' AND activated_at IS NOT NULL AND retired_at IS NOT NULL)
    );

ALTER TABLE app_users
    ADD CONSTRAINT uq_app_user_id_organization UNIQUE (id, organization_id);

CREATE TABLE knowledge_asset_publication_outbox (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    source_revision_id uuid NOT NULL,
    source_object_id uuid NOT NULL,
    knowledge_asset_id uuid NOT NULL,
    owner_user_id uuid NOT NULL,
    projection_generation bigint NOT NULL,
    embedding_profile_id uuid NOT NULL,
    embedding_dimensions integer NOT NULL,
    pipeline_version varchar(64) NOT NULL,
    status varchar(32) NOT NULL,
    attempt_count integer NOT NULL,
    authorization_model_id varchar(255),
    last_error_code varchar(64),
    last_error_message varchar(512),
    applied_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_knowledge_asset_publication_asset UNIQUE (knowledge_asset_id),
    CONSTRAINT fk_knowledge_asset_publication_revision
        FOREIGN KEY (source_revision_id, organization_id, source_object_id)
        REFERENCES source_revisions(id, organization_id, source_object_id),
    CONSTRAINT fk_knowledge_asset_publication_asset
        FOREIGN KEY (knowledge_asset_id, organization_id)
        REFERENCES knowledge_assets(id, organization_id),
    CONSTRAINT fk_knowledge_asset_publication_owner
        FOREIGN KEY (owner_user_id, organization_id)
        REFERENCES app_users(id, organization_id),
    CONSTRAINT fk_knowledge_asset_publication_embedding_profile
        FOREIGN KEY (embedding_profile_id, organization_id, embedding_dimensions)
        REFERENCES embedding_profiles(id, organization_id, dimensions),
    CONSTRAINT chk_knowledge_asset_publication_generation
        CHECK (projection_generation > 0),
    CONSTRAINT chk_knowledge_asset_publication_attempts
        CHECK (attempt_count >= 0),
    CONSTRAINT chk_knowledge_asset_publication_profile CHECK (
        embedding_dimensions > 0 AND btrim(pipeline_version) <> ''
    ),
    CONSTRAINT chk_knowledge_asset_publication_status
        CHECK (status IN ('PENDING', 'APPLIED')),
    CONSTRAINT chk_knowledge_asset_publication_applied CHECK (
        (status = 'PENDING' AND authorization_model_id IS NULL AND applied_at IS NULL)
        OR (status = 'APPLIED' AND authorization_model_id IS NOT NULL AND applied_at IS NOT NULL)
    )
);

CREATE INDEX idx_knowledge_asset_publication_pending
    ON knowledge_asset_publication_outbox (organization_id, created_at)
    WHERE status = 'PENDING';
