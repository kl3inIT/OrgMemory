-- source_type was answering two questions with one column: which system an object came
-- from, and which access rule governs it. Only the second is ever read — retrieval branches
-- on UPLOAD versus everything else, which is ADR 0009's rule that a live source enforces the
-- latest sealed ACL while an upload keeps ingestion intersected with current. Nothing
-- anywhere branches on the value 'SLACK'.
--
-- Because the source's name lived in that column it also lived in the column's check
-- constraint, so every new connector needed DDL to widen a constraint guarding a distinction
-- the name has nothing to do with. The two questions are separated here.
--
-- The column is renamed rather than kept, because 'source_type' is the name that invites
-- somebody to put a source's name back into it. acl_authority says what it decides, and the
-- retrieval SQL that reads it now explains itself.

ALTER TABLE source_objects ADD COLUMN source_system varchar(64);

-- Derived from what each row already claimed rather than assumed.
UPDATE source_objects
SET source_system = CASE WHEN source_type = 'UPLOAD' THEN 'upload' ELSE lower(source_type) END;

ALTER TABLE source_objects ALTER COLUMN source_system SET NOT NULL;

ALTER TABLE source_objects RENAME COLUMN source_type TO acl_authority;

UPDATE source_objects
SET acl_authority = CASE WHEN acl_authority = 'UPLOAD' THEN 'ORGMEMORY' ELSE 'SOURCE' END;

ALTER TABLE source_objects
    DROP CONSTRAINT chk_source_object_type,
    ADD CONSTRAINT chk_source_object_acl_authority
        CHECK (acl_authority IN ('ORGMEMORY', 'SOURCE')),
    ADD CONSTRAINT chk_source_object_system CHECK (btrim(source_system) <> '');

-- An object's identity is its system, its connection, and its external id. source_type was
-- in this key only because it used to carry the system; leaving it would now let two
-- different connectors collide on one connection key.
ALTER TABLE source_objects
    DROP CONSTRAINT uq_source_object_identity,
    ADD CONSTRAINT uq_source_object_identity UNIQUE (
        organization_id,
        source_system,
        source_connection_key,
        external_object_id
    );

COMMENT ON COLUMN source_objects.acl_authority IS
    'Who decides who may read this object. ORGMEMORY keeps the ingestion ACL intersected '
    'with the current one; SOURCE enforces only the latest sealed generation because the '
    'source still owns the decision. Recorded at ingestion and never updated: it is what was '
    'true when the evidence entered, not a policy an administrator can change afterwards.';

COMMENT ON COLUMN source_objects.source_system IS
    'Which system the object came from, such as slack or upload. Governed by the connector '
    'registry rather than a check constraint, so a new connector needs no migration.';
