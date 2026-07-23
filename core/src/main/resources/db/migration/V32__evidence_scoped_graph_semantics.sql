-- Entity/relation names and topology are canonical identities. Types,
-- descriptions, keywords, confidence, support and extraction fingerprints are
-- evidence-scoped so permission filtering always happens before semantic merge.
ALTER TABLE graph_entity_contributions
    ADD COLUMN entity_type varchar(128);

UPDATE graph_entity_contributions contribution
SET entity_type = entity.entity_type
FROM graph_entities entity
WHERE entity.organization_id = contribution.organization_id
  AND entity.id = contribution.entity_id;

ALTER TABLE graph_entity_contributions
    ALTER COLUMN entity_type SET NOT NULL;

ALTER TABLE graph_entity_contributions
    ADD CONSTRAINT chk_graph_entity_contribution_type
        CHECK (btrim(entity_type) <> '');

DROP INDEX idx_graph_entity_contribution_search;

ALTER TABLE graph_entity_contributions
    DROP COLUMN search_vector;

ALTER TABLE graph_entity_contributions
    ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
        to_tsvector(
            'simple',
            coalesce(entity_type, '') || ' ' || coalesce(description, '')
        )
    ) STORED;

CREATE INDEX idx_graph_entity_contribution_search
    ON graph_entity_contributions USING gin (search_vector);

DROP INDEX idx_graph_entity_identity_search;

ALTER TABLE graph_entities
    DROP CONSTRAINT chk_graph_entity_nonblank,
    DROP COLUMN search_vector,
    DROP COLUMN entity_type;

ALTER TABLE graph_entities
    ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
        to_tsvector('simple', coalesce(normalized_name, ''))
    ) STORED,
    ADD CONSTRAINT chk_graph_entity_nonblank
        CHECK (btrim(normalized_name) <> '');

CREATE INDEX idx_graph_entity_identity_search
    ON graph_entities USING gin (search_vector);

ALTER TABLE graph_entity_contributions
    ADD COLUMN extraction_profile_fingerprint char(64) NOT NULL
        DEFAULT repeat('0', 64);

ALTER TABLE graph_entity_contributions
    ALTER COLUMN extraction_profile_fingerprint DROP DEFAULT,
    ADD CONSTRAINT chk_graph_entity_contribution_profile_fingerprint
        CHECK (extraction_profile_fingerprint ~ '^[0-9a-f]{64}$');

ALTER TABLE graph_relation_contributions
    ADD COLUMN relation_type varchar(128);

UPDATE graph_relation_contributions contribution
SET relation_type = relation.relation_type
FROM graph_relations relation
WHERE relation.organization_id = contribution.organization_id
  AND relation.id = contribution.relation_id;

ALTER TABLE graph_relation_contributions
    ALTER COLUMN relation_type SET NOT NULL,
    ADD CONSTRAINT chk_graph_relation_contribution_type
        CHECK (btrim(relation_type) <> '');

DROP INDEX idx_graph_relation_contribution_search;

ALTER TABLE graph_relation_contributions
    DROP COLUMN search_vector;

ALTER TABLE graph_relation_contributions
    ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
        to_tsvector(
            'simple',
            coalesce(relation_type, '') || ' ' || coalesce(search_content, '')
        )
    ) STORED;

CREATE INDEX idx_graph_relation_contribution_search
    ON graph_relation_contributions USING gin (search_vector);

DROP INDEX idx_graph_relation_identity_search;

ALTER TABLE graph_relations
    DROP CONSTRAINT chk_graph_relation_nonblank,
    DROP COLUMN search_vector,
    DROP COLUMN relation_type;

ALTER TABLE graph_relation_contributions
    ADD COLUMN weight double precision NOT NULL DEFAULT 1.0;

ALTER TABLE graph_relation_contributions
    ADD CONSTRAINT chk_graph_relation_contribution_weight
        CHECK (weight > 0.0 AND weight < 'Infinity'::float8);

ALTER TABLE graph_relation_contributions
    ALTER COLUMN weight DROP DEFAULT;

ALTER TABLE graph_relation_contributions
    ADD COLUMN extraction_profile_fingerprint char(64) NOT NULL
        DEFAULT repeat('0', 64);

ALTER TABLE graph_relation_contributions
    ALTER COLUMN extraction_profile_fingerprint DROP DEFAULT,
    ADD CONSTRAINT chk_graph_relation_contribution_profile_fingerprint
        CHECK (extraction_profile_fingerprint ~ '^[0-9a-f]{64}$');
