-- Entity/relation names and topology are canonical identities. Types,
-- descriptions, keywords, confidence, support and extraction fingerprints are
-- evidence-scoped so permission filtering always happens before semantic merge.
--
-- This migration is deliberately non-transactional so large backfills can
-- commit in bounded batches. Do not use CREATE INDEX CONCURRENTLY from
-- application-owned Flyway: its schema-history connection retains a snapshot
-- and can block the concurrent build indefinitely. The graph projection is
-- unreleased and these indexes are built before production traffic; a future
-- large-table index replacement must be pre-staged by the deployment pipeline
-- according to docs/conventions.md.

ALTER TABLE graph_entity_contributions
    ADD COLUMN entity_type varchar(128);

CREATE PROCEDURE backfill_graph_entity_contribution_types(batch_size integer)
LANGUAGE plpgsql
AS $$
DECLARE
    affected_rows integer;
BEGIN
    LOOP
        WITH pending AS (
            SELECT
                contribution.organization_id,
                contribution.id,
                entity.entity_type
            FROM graph_entity_contributions contribution
            JOIN graph_entities entity
              ON entity.organization_id = contribution.organization_id
             AND entity.id = contribution.entity_id
            WHERE contribution.entity_type IS NULL
            ORDER BY contribution.organization_id, contribution.id
            LIMIT batch_size
            FOR UPDATE OF contribution SKIP LOCKED
        )
        UPDATE graph_entity_contributions contribution
        SET entity_type = pending.entity_type
        FROM pending
        WHERE contribution.organization_id = pending.organization_id
          AND contribution.id = pending.id;

        GET DIAGNOSTICS affected_rows = ROW_COUNT;
        COMMIT;
        EXIT WHEN affected_rows = 0;
    END LOOP;
END;
$$;

CALL backfill_graph_entity_contribution_types(1000);
DROP PROCEDURE backfill_graph_entity_contribution_types(integer);

ALTER TABLE graph_entity_contributions
    ADD CONSTRAINT chk_graph_entity_contribution_type
        CHECK (entity_type IS NOT NULL AND btrim(entity_type) <> '')
        NOT VALID;

ALTER TABLE graph_entity_contributions
    VALIDATE CONSTRAINT chk_graph_entity_contribution_type;

DROP INDEX IF EXISTS idx_graph_entity_contribution_search;

ALTER TABLE graph_entity_contributions
    DROP COLUMN search_vector,
    ADD COLUMN search_vector tsvector;

CREATE FUNCTION set_graph_entity_contribution_search_vector()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.search_vector := to_tsvector(
        'simple',
        coalesce(NEW.entity_type, '') || ' ' || coalesce(NEW.description, '')
    );
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_graph_entity_contribution_search_vector
BEFORE INSERT OR UPDATE OF entity_type, description
ON graph_entity_contributions
FOR EACH ROW
EXECUTE FUNCTION set_graph_entity_contribution_search_vector();

CREATE PROCEDURE backfill_graph_entity_contribution_search(batch_size integer)
LANGUAGE plpgsql
AS $$
DECLARE
    affected_rows integer;
BEGIN
    LOOP
        WITH pending AS (
            SELECT organization_id, id
            FROM graph_entity_contributions
            WHERE search_vector IS NULL
            ORDER BY organization_id, id
            LIMIT batch_size
            FOR UPDATE SKIP LOCKED
        )
        UPDATE graph_entity_contributions contribution
        SET search_vector = to_tsvector(
            'simple',
            coalesce(contribution.entity_type, '')
                || ' '
                || coalesce(contribution.description, '')
        )
        FROM pending
        WHERE contribution.organization_id = pending.organization_id
          AND contribution.id = pending.id;

        GET DIAGNOSTICS affected_rows = ROW_COUNT;
        COMMIT;
        EXIT WHEN affected_rows = 0;
    END LOOP;
END;
$$;

CALL backfill_graph_entity_contribution_search(1000);
DROP PROCEDURE backfill_graph_entity_contribution_search(integer);

CREATE INDEX idx_graph_entity_contribution_search
    ON graph_entity_contributions USING gin (search_vector);

DROP INDEX IF EXISTS idx_graph_entity_identity_search;

ALTER TABLE graph_entities
    DROP CONSTRAINT chk_graph_entity_nonblank,
    DROP COLUMN search_vector,
    DROP COLUMN entity_type,
    ADD COLUMN search_vector tsvector;

ALTER TABLE graph_entities
    ADD CONSTRAINT chk_graph_entity_nonblank
        CHECK (btrim(normalized_name) <> '')
        NOT VALID;

ALTER TABLE graph_entities
    VALIDATE CONSTRAINT chk_graph_entity_nonblank;

CREATE FUNCTION set_graph_entity_identity_search_vector()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.search_vector := to_tsvector(
        'simple',
        coalesce(NEW.normalized_name, '')
    );
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_graph_entity_identity_search_vector
BEFORE INSERT OR UPDATE OF normalized_name
ON graph_entities
FOR EACH ROW
EXECUTE FUNCTION set_graph_entity_identity_search_vector();

CREATE PROCEDURE backfill_graph_entity_identity_search(batch_size integer)
LANGUAGE plpgsql
AS $$
DECLARE
    affected_rows integer;
BEGIN
    LOOP
        WITH pending AS (
            SELECT organization_id, id
            FROM graph_entities
            WHERE search_vector IS NULL
            ORDER BY organization_id, id
            LIMIT batch_size
            FOR UPDATE SKIP LOCKED
        )
        UPDATE graph_entities entity
        SET search_vector = to_tsvector(
            'simple',
            coalesce(entity.normalized_name, '')
        )
        FROM pending
        WHERE entity.organization_id = pending.organization_id
          AND entity.id = pending.id;

        GET DIAGNOSTICS affected_rows = ROW_COUNT;
        COMMIT;
        EXIT WHEN affected_rows = 0;
    END LOOP;
END;
$$;

CALL backfill_graph_entity_identity_search(1000);
DROP PROCEDURE backfill_graph_entity_identity_search(integer);

CREATE INDEX idx_graph_entity_identity_search
    ON graph_entities USING gin (search_vector);

ALTER TABLE graph_entity_contributions
    ADD COLUMN extraction_profile_fingerprint text
        DEFAULT repeat('0', 64);

ALTER TABLE graph_entity_contributions
    ALTER COLUMN extraction_profile_fingerprint DROP DEFAULT,
    ADD CONSTRAINT chk_graph_entity_contribution_profile_fingerprint
        CHECK (
            extraction_profile_fingerprint IS NOT NULL
            AND extraction_profile_fingerprint ~ '^[0-9a-f]{64}$'
        )
        NOT VALID;

ALTER TABLE graph_entity_contributions
    VALIDATE CONSTRAINT chk_graph_entity_contribution_profile_fingerprint;

ALTER TABLE graph_relation_contributions
    ADD COLUMN relation_type varchar(128);

CREATE PROCEDURE backfill_graph_relation_contribution_types(batch_size integer)
LANGUAGE plpgsql
AS $$
DECLARE
    affected_rows integer;
BEGIN
    LOOP
        WITH pending AS (
            SELECT
                contribution.organization_id,
                contribution.id,
                relation.relation_type
            FROM graph_relation_contributions contribution
            JOIN graph_relations relation
              ON relation.organization_id = contribution.organization_id
             AND relation.id = contribution.relation_id
            WHERE contribution.relation_type IS NULL
            ORDER BY contribution.organization_id, contribution.id
            LIMIT batch_size
            FOR UPDATE OF contribution SKIP LOCKED
        )
        UPDATE graph_relation_contributions contribution
        SET relation_type = pending.relation_type
        FROM pending
        WHERE contribution.organization_id = pending.organization_id
          AND contribution.id = pending.id;

        GET DIAGNOSTICS affected_rows = ROW_COUNT;
        COMMIT;
        EXIT WHEN affected_rows = 0;
    END LOOP;
END;
$$;

CALL backfill_graph_relation_contribution_types(1000);
DROP PROCEDURE backfill_graph_relation_contribution_types(integer);

ALTER TABLE graph_relation_contributions
    ADD CONSTRAINT chk_graph_relation_contribution_type
        CHECK (relation_type IS NOT NULL AND btrim(relation_type) <> '')
        NOT VALID;

ALTER TABLE graph_relation_contributions
    VALIDATE CONSTRAINT chk_graph_relation_contribution_type;

DROP INDEX IF EXISTS idx_graph_relation_contribution_search;

ALTER TABLE graph_relation_contributions
    DROP COLUMN search_vector,
    ADD COLUMN search_vector tsvector;

CREATE FUNCTION set_graph_relation_contribution_search_vector()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.search_vector := to_tsvector(
        'simple',
        coalesce(NEW.relation_type, '')
            || ' '
            || coalesce(NEW.search_content, '')
    );
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_graph_relation_contribution_search_vector
BEFORE INSERT OR UPDATE OF relation_type, search_content
ON graph_relation_contributions
FOR EACH ROW
EXECUTE FUNCTION set_graph_relation_contribution_search_vector();

CREATE PROCEDURE backfill_graph_relation_contribution_search(batch_size integer)
LANGUAGE plpgsql
AS $$
DECLARE
    affected_rows integer;
BEGIN
    LOOP
        WITH pending AS (
            SELECT organization_id, id
            FROM graph_relation_contributions
            WHERE search_vector IS NULL
            ORDER BY organization_id, id
            LIMIT batch_size
            FOR UPDATE SKIP LOCKED
        )
        UPDATE graph_relation_contributions contribution
        SET search_vector = to_tsvector(
            'simple',
            coalesce(contribution.relation_type, '')
                || ' '
                || coalesce(contribution.search_content, '')
        )
        FROM pending
        WHERE contribution.organization_id = pending.organization_id
          AND contribution.id = pending.id;

        GET DIAGNOSTICS affected_rows = ROW_COUNT;
        COMMIT;
        EXIT WHEN affected_rows = 0;
    END LOOP;
END;
$$;

CALL backfill_graph_relation_contribution_search(1000);
DROP PROCEDURE backfill_graph_relation_contribution_search(integer);

CREATE INDEX idx_graph_relation_contribution_search
    ON graph_relation_contributions USING gin (search_vector);

DROP INDEX IF EXISTS idx_graph_relation_identity_search;

ALTER TABLE graph_relations
    DROP CONSTRAINT chk_graph_relation_nonblank,
    DROP COLUMN search_vector,
    DROP COLUMN relation_type;

ALTER TABLE graph_relation_contributions
    ADD COLUMN weight double precision DEFAULT 1.0;

ALTER TABLE graph_relation_contributions
    ALTER COLUMN weight DROP DEFAULT,
    ADD CONSTRAINT chk_graph_relation_contribution_weight
        CHECK (
            weight IS NOT NULL
            AND weight > 0.0
            AND weight < 'Infinity'::float8
        )
        NOT VALID;

ALTER TABLE graph_relation_contributions
    VALIDATE CONSTRAINT chk_graph_relation_contribution_weight;

ALTER TABLE graph_relation_contributions
    ADD COLUMN extraction_profile_fingerprint text
        DEFAULT repeat('0', 64);

ALTER TABLE graph_relation_contributions
    ALTER COLUMN extraction_profile_fingerprint DROP DEFAULT,
    ADD CONSTRAINT chk_graph_relation_contribution_profile_fingerprint
        CHECK (
            extraction_profile_fingerprint IS NOT NULL
            AND extraction_profile_fingerprint ~ '^[0-9a-f]{64}$'
        )
        NOT VALID;

ALTER TABLE graph_relation_contributions
    VALIDATE CONSTRAINT chk_graph_relation_contribution_profile_fingerprint;
