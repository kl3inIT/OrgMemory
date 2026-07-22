CREATE TABLE knowledge_spaces (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    department_id uuid,
    space_key varchar(128) NOT NULL,
    name varchar(255) NOT NULL,
    active boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_knowledge_space_key UNIQUE (organization_id, space_key),
    CONSTRAINT uq_knowledge_space_id_organization UNIQUE (id, organization_id),
    CONSTRAINT fk_knowledge_space_department_organization
        FOREIGN KEY (department_id, organization_id)
        REFERENCES departments(id, organization_id),
    CONSTRAINT chk_knowledge_space_nonblank CHECK (
        btrim(space_key) <> '' AND btrim(name) <> ''
    )
);

INSERT INTO knowledge_spaces (
    id, organization_id, department_id, space_key, name, active,
    created_at, updated_at, version
)
SELECT
    '88888888-8888-4888-8888-888888888801',
    id,
    NULL,
    'company',
    'Company Knowledge',
    true,
    now(),
    now(),
    0
FROM organizations
WHERE id = '11111111-1111-1111-1111-111111111111';

INSERT INTO knowledge_spaces (
    id, organization_id, department_id, space_key, name, active,
    created_at, updated_at, version
)
SELECT
    '88888888-8888-4888-8888-888888888802',
    organization_id,
    id,
    'sales',
    'Sales Knowledge',
    true,
    now(),
    now(),
    0
FROM departments
WHERE id = '22222222-2222-2222-2222-222222222222';

INSERT INTO knowledge_spaces (
    id, organization_id, department_id, space_key, name, active,
    created_at, updated_at, version
)
SELECT
    gen_random_uuid(),
    organization.id,
    NULL,
    'company',
    organization.name || ' Knowledge',
    true,
    now(),
    now(),
    0
FROM organizations organization
WHERE NOT EXISTS (
    SELECT 1
    FROM knowledge_spaces space
    WHERE space.organization_id = organization.id
      AND space.space_key = 'company'
);

ALTER TABLE source_objects
    ADD COLUMN knowledge_space_id uuid;

ALTER TABLE source_objects
    ADD CONSTRAINT fk_source_object_space_organization
        FOREIGN KEY (knowledge_space_id, organization_id)
        REFERENCES knowledge_spaces(id, organization_id)
        NOT VALID;

CREATE PROCEDURE backfill_source_object_spaces(batch_size integer)
LANGUAGE plpgsql
AS $$
DECLARE
    affected_rows integer;
BEGIN
    LOOP
        WITH pending AS (
            SELECT id, organization_id
            FROM source_objects
            WHERE knowledge_space_id IS NULL
            ORDER BY id
            LIMIT batch_size
            FOR UPDATE SKIP LOCKED
        )
        UPDATE source_objects source
        SET knowledge_space_id = COALESCE(
            (
                SELECT space.id
                FROM knowledge_spaces space
                WHERE space.organization_id = source.organization_id
                  AND space.department_id = source.department_id
                ORDER BY space.space_key
                LIMIT 1
            ),
            (
                SELECT space.id
                FROM knowledge_spaces space
                WHERE space.organization_id = source.organization_id
                  AND space.space_key = 'company'
            )
        )
        FROM pending
        WHERE source.id = pending.id
          AND source.organization_id = pending.organization_id;

        GET DIAGNOSTICS affected_rows = ROW_COUNT;
        COMMIT;
        EXIT WHEN affected_rows = 0;
    END LOOP;
END;
$$;

CALL backfill_source_object_spaces(1000);
DROP PROCEDURE backfill_source_object_spaces(integer);

ALTER TABLE source_objects
    ADD CONSTRAINT chk_source_object_space_present
        CHECK (knowledge_space_id IS NOT NULL) NOT VALID;
ALTER TABLE source_objects VALIDATE CONSTRAINT fk_source_object_space_organization;
ALTER TABLE source_objects VALIDATE CONSTRAINT chk_source_object_space_present;
ALTER TABLE source_objects ALTER COLUMN knowledge_space_id SET NOT NULL;
ALTER TABLE source_objects DROP CONSTRAINT chk_source_object_space_present;

ALTER TABLE source_revisions
    ADD COLUMN knowledge_space_id uuid;

ALTER TABLE source_revisions
    ADD CONSTRAINT fk_source_revision_space_organization
        FOREIGN KEY (knowledge_space_id, organization_id)
        REFERENCES knowledge_spaces(id, organization_id)
        NOT VALID;

CREATE PROCEDURE backfill_source_revision_spaces(batch_size integer)
LANGUAGE plpgsql
AS $$
DECLARE
    affected_rows integer;
BEGIN
    LOOP
        WITH pending AS (
            SELECT id, organization_id
            FROM source_revisions
            WHERE knowledge_space_id IS NULL
            ORDER BY id
            LIMIT batch_size
            FOR UPDATE SKIP LOCKED
        )
        UPDATE source_revisions revision
        SET knowledge_space_id = source.knowledge_space_id
        FROM pending, source_objects source
        WHERE revision.id = pending.id
          AND revision.organization_id = pending.organization_id
          AND source.id = revision.source_object_id
          AND source.organization_id = revision.organization_id;

        GET DIAGNOSTICS affected_rows = ROW_COUNT;
        COMMIT;
        EXIT WHEN affected_rows = 0;
    END LOOP;
END;
$$;

CALL backfill_source_revision_spaces(1000);
DROP PROCEDURE backfill_source_revision_spaces(integer);

ALTER TABLE source_revisions
    ADD CONSTRAINT chk_source_revision_space_present
        CHECK (knowledge_space_id IS NOT NULL) NOT VALID;
ALTER TABLE source_revisions VALIDATE CONSTRAINT fk_source_revision_space_organization;
ALTER TABLE source_revisions VALIDATE CONSTRAINT chk_source_revision_space_present;
ALTER TABLE source_revisions ALTER COLUMN knowledge_space_id SET NOT NULL;
ALTER TABLE source_revisions DROP CONSTRAINT chk_source_revision_space_present;

ALTER TABLE knowledge_assets
    ADD COLUMN knowledge_space_id uuid;

ALTER TABLE knowledge_assets
    ADD CONSTRAINT fk_knowledge_asset_space_organization
        FOREIGN KEY (knowledge_space_id, organization_id)
        REFERENCES knowledge_spaces(id, organization_id)
        NOT VALID;

CREATE PROCEDURE backfill_knowledge_asset_spaces(batch_size integer)
LANGUAGE plpgsql
AS $$
DECLARE
    affected_rows integer;
BEGIN
    LOOP
        WITH pending AS (
            SELECT id, organization_id
            FROM knowledge_assets
            WHERE knowledge_space_id IS NULL
            ORDER BY id
            LIMIT batch_size
            FOR UPDATE SKIP LOCKED
        )
        UPDATE knowledge_assets asset
        SET knowledge_space_id = COALESCE(
            (
                SELECT source.knowledge_space_id
                FROM source_revisions revision
                JOIN source_objects source
                  ON source.id = revision.source_object_id
                 AND source.organization_id = revision.organization_id
                WHERE revision.knowledge_asset_id = asset.id
                  AND revision.organization_id = asset.organization_id
                LIMIT 1
            ),
            (
                SELECT space.id
                FROM knowledge_spaces space
                WHERE space.organization_id = asset.organization_id
                  AND space.space_key = 'company'
            )
        )
        FROM pending
        WHERE asset.id = pending.id
          AND asset.organization_id = pending.organization_id;

        GET DIAGNOSTICS affected_rows = ROW_COUNT;
        COMMIT;
        EXIT WHEN affected_rows = 0;
    END LOOP;
END;
$$;

CALL backfill_knowledge_asset_spaces(1000);
DROP PROCEDURE backfill_knowledge_asset_spaces(integer);

ALTER TABLE knowledge_assets
    ADD CONSTRAINT chk_knowledge_asset_space_present
        CHECK (knowledge_space_id IS NOT NULL) NOT VALID;
ALTER TABLE knowledge_assets VALIDATE CONSTRAINT fk_knowledge_asset_space_organization;
ALTER TABLE knowledge_assets VALIDATE CONSTRAINT chk_knowledge_asset_space_present;
ALTER TABLE knowledge_assets ALTER COLUMN knowledge_space_id SET NOT NULL;
ALTER TABLE knowledge_assets DROP CONSTRAINT chk_knowledge_asset_space_present;

ALTER TABLE knowledge_asset_publication_outbox
    ADD COLUMN knowledge_space_id uuid;

ALTER TABLE knowledge_asset_publication_outbox
    ADD CONSTRAINT fk_knowledge_asset_publication_space_organization
        FOREIGN KEY (knowledge_space_id, organization_id)
        REFERENCES knowledge_spaces(id, organization_id)
        NOT VALID;

CREATE PROCEDURE backfill_knowledge_asset_publication_spaces(batch_size integer)
LANGUAGE plpgsql
AS $$
DECLARE
    affected_rows integer;
BEGIN
    LOOP
        WITH pending AS (
            SELECT id, organization_id
            FROM knowledge_asset_publication_outbox
            WHERE knowledge_space_id IS NULL
            ORDER BY id
            LIMIT batch_size
            FOR UPDATE SKIP LOCKED
        )
        UPDATE knowledge_asset_publication_outbox publication
        SET knowledge_space_id = source.knowledge_space_id
        FROM pending, source_objects source
        WHERE publication.id = pending.id
          AND publication.organization_id = pending.organization_id
          AND source.id = publication.source_object_id
          AND source.organization_id = publication.organization_id;

        GET DIAGNOSTICS affected_rows = ROW_COUNT;
        COMMIT;
        EXIT WHEN affected_rows = 0;
    END LOOP;
END;
$$;

CALL backfill_knowledge_asset_publication_spaces(1000);
DROP PROCEDURE backfill_knowledge_asset_publication_spaces(integer);

ALTER TABLE knowledge_asset_publication_outbox
    ADD CONSTRAINT chk_knowledge_asset_publication_space_present
        CHECK (knowledge_space_id IS NOT NULL) NOT VALID;
ALTER TABLE knowledge_asset_publication_outbox
    VALIDATE CONSTRAINT fk_knowledge_asset_publication_space_organization;
ALTER TABLE knowledge_asset_publication_outbox
    VALIDATE CONSTRAINT chk_knowledge_asset_publication_space_present;
ALTER TABLE knowledge_asset_publication_outbox
    ALTER COLUMN knowledge_space_id SET NOT NULL;
ALTER TABLE knowledge_asset_publication_outbox
    DROP CONSTRAINT chk_knowledge_asset_publication_space_present;

CREATE INDEX idx_knowledge_space_active
    ON knowledge_spaces (organization_id, active, name);
-- Concurrent index builds are intentionally not run by application-owned Flyway.
-- Large-table deployments must pre-stage equivalent concurrent indexes through
-- the deployment pipeline before enabling this migration.
CREATE INDEX idx_source_object_space
    ON source_objects (organization_id, knowledge_space_id, updated_at DESC);
CREATE INDEX idx_knowledge_asset_space
    ON knowledge_assets (organization_id, knowledge_space_id, status);
