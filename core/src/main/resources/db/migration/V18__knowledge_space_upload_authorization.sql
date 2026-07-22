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
);

ALTER TABLE source_objects
    ALTER COLUMN knowledge_space_id SET NOT NULL,
    ADD CONSTRAINT fk_source_object_space_organization
        FOREIGN KEY (knowledge_space_id, organization_id)
        REFERENCES knowledge_spaces(id, organization_id);

ALTER TABLE source_revisions
    ADD COLUMN knowledge_space_id uuid;

UPDATE source_revisions revision
SET knowledge_space_id = source.knowledge_space_id
FROM source_objects source
WHERE source.id = revision.source_object_id
  AND source.organization_id = revision.organization_id;

ALTER TABLE source_revisions
    ALTER COLUMN knowledge_space_id SET NOT NULL,
    ADD CONSTRAINT fk_source_revision_space_organization
        FOREIGN KEY (knowledge_space_id, organization_id)
        REFERENCES knowledge_spaces(id, organization_id);

ALTER TABLE knowledge_assets
    ADD COLUMN knowledge_space_id uuid;

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
);

ALTER TABLE knowledge_assets
    ALTER COLUMN knowledge_space_id SET NOT NULL,
    ADD CONSTRAINT fk_knowledge_asset_space_organization
        FOREIGN KEY (knowledge_space_id, organization_id)
        REFERENCES knowledge_spaces(id, organization_id);

ALTER TABLE knowledge_asset_publication_outbox
    ADD COLUMN knowledge_space_id uuid;

UPDATE knowledge_asset_publication_outbox publication
SET knowledge_space_id = source.knowledge_space_id
FROM source_objects source
WHERE source.id = publication.source_object_id
  AND source.organization_id = publication.organization_id;

ALTER TABLE knowledge_asset_publication_outbox
    ALTER COLUMN knowledge_space_id SET NOT NULL,
    ADD CONSTRAINT fk_knowledge_asset_publication_space_organization
        FOREIGN KEY (knowledge_space_id, organization_id)
        REFERENCES knowledge_spaces(id, organization_id);

CREATE INDEX idx_knowledge_space_active
    ON knowledge_spaces (organization_id, active, name);
CREATE INDEX idx_source_object_space
    ON source_objects (organization_id, knowledge_space_id, updated_at DESC);
CREATE INDEX idx_knowledge_asset_space
    ON knowledge_assets (organization_id, knowledge_space_id, status);
