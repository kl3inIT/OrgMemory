ALTER TABLE app_users
    ADD COLUMN active boolean NOT NULL DEFAULT true;

ALTER TABLE departments
    ADD CONSTRAINT uq_departments_id_organization UNIQUE (id, organization_id);

CREATE TABLE raw_source_objects (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    department_id uuid,
    source_system varchar(64) NOT NULL,
    source_connection_key varchar(128) NOT NULL,
    external_object_id varchar(512) NOT NULL,
    source_version varchar(255) NOT NULL,
    object_type varchar(64) NOT NULL,
    title varchar(255) NOT NULL,
    raw_content text NOT NULL,
    source_uri varchar(2048),
    payload_sha256 varchar(64) NOT NULL,
    source_modified_at timestamptz,
    classification varchar(32),
    declared_access varchar(32),
    status varchar(32) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_raw_source_revision UNIQUE (
        organization_id,
        source_system,
        source_connection_key,
        external_object_id,
        source_version
    ),
    CONSTRAINT uq_raw_source_id_organization UNIQUE (id, organization_id),
    CONSTRAINT uq_raw_source_identity_chain UNIQUE (
        id,
        organization_id,
        source_system,
        source_connection_key,
        external_object_id
    ),
    CONSTRAINT fk_raw_source_department_organization
        FOREIGN KEY (department_id, organization_id)
        REFERENCES departments(id, organization_id),
    CONSTRAINT chk_raw_source_nonblank CHECK (
        btrim(source_system) <> ''
        AND btrim(source_connection_key) <> ''
        AND btrim(external_object_id) <> ''
        AND btrim(source_version) <> ''
        AND btrim(object_type) <> ''
        AND btrim(title) <> ''
        AND btrim(raw_content) <> ''
    ),
    CONSTRAINT chk_raw_source_payload_sha CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_raw_source_classification CHECK (
        classification IS NULL
        OR classification IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED')
    ),
    CONSTRAINT chk_raw_source_declared_access CHECK (
        declared_access IS NULL
        OR declared_access IN ('ALL', 'ALL_EMPLOYEES', 'OWN_DEPARTMENT', 'EXECUTIVE_ONLY')
    ),
    CONSTRAINT chk_raw_source_status CHECK (status IN ('RECEIVED', 'NORMALIZED', 'REJECTED'))
);

CREATE TABLE source_acl_snapshots (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    raw_source_object_id uuid NOT NULL,
    acl_generation bigint NOT NULL,
    capture_status varchar(32) NOT NULL,
    default_gate varchar(16) NOT NULL,
    acl_sha256 varchar(64),
    captured_at timestamptz NOT NULL,
    valid_until timestamptz,
    CONSTRAINT uq_source_acl_raw_generation UNIQUE (raw_source_object_id, acl_generation),
    CONSTRAINT uq_source_acl_id_organization UNIQUE (id, organization_id),
    CONSTRAINT uq_source_acl_id_organization_raw
        UNIQUE (id, organization_id, raw_source_object_id),
    CONSTRAINT uq_source_acl_generation_chain
        UNIQUE (id, organization_id, raw_source_object_id, acl_generation),
    CONSTRAINT fk_source_acl_raw_organization
        FOREIGN KEY (raw_source_object_id, organization_id)
        REFERENCES raw_source_objects(id, organization_id),
    CONSTRAINT chk_source_acl_capture_status
        CHECK (capture_status IN ('COMPLETE', 'UNKNOWN', 'UNSUPPORTED')),
    CONSTRAINT chk_source_acl_default_gate
        CHECK (default_gate IN ('ALLOW', 'DENY', 'UNKNOWN')),
    CONSTRAINT chk_source_acl_sha
        CHECK (acl_sha256 IS NULL OR acl_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_source_acl_generation CHECK (acl_generation > 0),
    CONSTRAINT chk_source_acl_completeness CHECK (
        (
            capture_status = 'COMPLETE'
            AND acl_sha256 IS NOT NULL
            AND valid_until IS NOT NULL
            AND valid_until > captured_at
            AND valid_until <= captured_at + interval '24 hours'
        )
        OR (
            capture_status IN ('UNKNOWN', 'UNSUPPORTED')
            AND default_gate = 'UNKNOWN'
            AND valid_until IS NULL
        )
    )
);

CREATE TABLE source_acl_heads (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    source_system varchar(64) NOT NULL,
    source_connection_key varchar(128) NOT NULL,
    external_object_id varchar(512) NOT NULL,
    current_raw_source_object_id uuid NOT NULL,
    current_snapshot_id uuid NOT NULL,
    acl_generation bigint NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_source_acl_head_identity UNIQUE (
        organization_id,
        source_system,
        source_connection_key,
        external_object_id
    ),
    CONSTRAINT fk_source_acl_head_snapshot_chain
        FOREIGN KEY (
            current_snapshot_id,
            organization_id,
            current_raw_source_object_id,
            acl_generation
        )
        REFERENCES source_acl_snapshots(
            id,
            organization_id,
            raw_source_object_id,
            acl_generation
        ),
    CONSTRAINT fk_source_acl_head_raw_identity
        FOREIGN KEY (
            current_raw_source_object_id,
            organization_id,
            source_system,
            source_connection_key,
            external_object_id
        )
        REFERENCES raw_source_objects(
            id,
            organization_id,
            source_system,
            source_connection_key,
            external_object_id
        ),
    CONSTRAINT chk_source_acl_head_nonblank CHECK (
        btrim(source_system) <> ''
        AND btrim(source_connection_key) <> ''
        AND btrim(external_object_id) <> ''
    ),
    CONSTRAINT chk_source_acl_head_generation CHECK (acl_generation > 0)
);

CREATE TABLE source_acl_entries (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    source_acl_snapshot_id uuid NOT NULL,
    principal_type varchar(32) NOT NULL,
    principal_key varchar(512) NOT NULL,
    gate varchar(16) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_source_acl_principal UNIQUE (
        source_acl_snapshot_id,
        principal_type,
        principal_key
    ),
    CONSTRAINT fk_source_acl_entry_snapshot_organization
        FOREIGN KEY (source_acl_snapshot_id, organization_id)
        REFERENCES source_acl_snapshots(id, organization_id),
    CONSTRAINT chk_source_acl_principal_type
        CHECK (principal_type IN (
            'ORGMEMORY_USER',
            'ORGMEMORY_DEPARTMENT',
            'ORGMEMORY_ORGANIZATION'
        )),
    CONSTRAINT chk_source_acl_principal_key CHECK (btrim(principal_key) <> ''),
    CONSTRAINT chk_source_acl_entry_gate CHECK (gate IN ('ALLOW', 'DENY'))
);

CREATE TABLE source_acl_snapshot_seals (
    source_acl_snapshot_id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    entry_count integer NOT NULL,
    entries_sha256 varchar(64) NOT NULL,
    sealed_at timestamptz NOT NULL,
    CONSTRAINT uq_source_acl_seal_snapshot_organization
        UNIQUE (source_acl_snapshot_id, organization_id),
    CONSTRAINT fk_source_acl_seal_snapshot_organization
        FOREIGN KEY (source_acl_snapshot_id, organization_id)
        REFERENCES source_acl_snapshots(id, organization_id),
    CONSTRAINT chk_source_acl_seal_entry_count CHECK (entry_count >= 0),
    CONSTRAINT chk_source_acl_seal_sha CHECK (entries_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE FUNCTION reject_entry_insert_into_sealed_acl()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM source_acl_snapshot_seals seal
        WHERE seal.source_acl_snapshot_id = NEW.source_acl_snapshot_id
          AND seal.organization_id = NEW.organization_id
    ) THEN
        RAISE EXCEPTION 'source ACL snapshot is sealed'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER source_acl_entries_reject_after_seal
BEFORE INSERT
ON source_acl_entries
FOR EACH ROW
EXECUTE FUNCTION reject_entry_insert_into_sealed_acl();

CREATE FUNCTION validate_source_acl_seal()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    actual_entry_count integer;
BEGIN
    SELECT count(*)
    INTO actual_entry_count
    FROM source_acl_entries entry
    WHERE entry.source_acl_snapshot_id = NEW.source_acl_snapshot_id
      AND entry.organization_id = NEW.organization_id;

    IF actual_entry_count <> NEW.entry_count THEN
        RAISE EXCEPTION 'source ACL seal entry count does not match snapshot entries'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER source_acl_snapshot_seal_validate
BEFORE INSERT
ON source_acl_snapshot_seals
FOR EACH ROW
EXECUTE FUNCTION validate_source_acl_seal();

ALTER TABLE source_acl_heads
    ADD CONSTRAINT fk_source_acl_head_current_seal
    FOREIGN KEY (current_snapshot_id, organization_id)
    REFERENCES source_acl_snapshot_seals(source_acl_snapshot_id, organization_id);

CREATE FUNCTION reject_source_acl_evidence_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'source ACL evidence is append-only'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER source_acl_snapshots_append_only
BEFORE UPDATE OR DELETE OR TRUNCATE
ON source_acl_snapshots
FOR EACH STATEMENT
EXECUTE FUNCTION reject_source_acl_evidence_mutation();

CREATE TRIGGER source_acl_entries_append_only
BEFORE UPDATE OR DELETE OR TRUNCATE
ON source_acl_entries
FOR EACH STATEMENT
EXECUTE FUNCTION reject_source_acl_evidence_mutation();

CREATE TRIGGER source_acl_snapshot_seals_append_only
BEFORE UPDATE OR DELETE OR TRUNCATE
ON source_acl_snapshot_seals
FOR EACH STATEMENT
EXECUTE FUNCTION reject_source_acl_evidence_mutation();

ALTER TABLE source_acl_snapshots
    ENABLE ALWAYS TRIGGER source_acl_snapshots_append_only;

ALTER TABLE source_acl_entries
    ENABLE ALWAYS TRIGGER source_acl_entries_append_only;

ALTER TABLE source_acl_snapshot_seals
    ENABLE ALWAYS TRIGGER source_acl_snapshot_seals_append_only;

ALTER TABLE permission_audit_events
    ADD COLUMN ingestion_acl_snapshot_id uuid,
    ADD COLUMN current_acl_snapshot_id uuid,
    ADD CONSTRAINT fk_permission_audit_ingestion_acl
        FOREIGN KEY (ingestion_acl_snapshot_id, organization_id)
        REFERENCES source_acl_snapshots(id, organization_id),
    ADD CONSTRAINT fk_permission_audit_current_acl
        FOREIGN KEY (current_acl_snapshot_id, organization_id)
        REFERENCES source_acl_snapshots(id, organization_id);

CREATE TABLE normalized_records (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    raw_source_object_id uuid NOT NULL,
    source_acl_snapshot_id uuid NOT NULL,
    normalizer_version varchar(64) NOT NULL,
    title varchar(255),
    normalized_content text,
    language varchar(16),
    department_id uuid,
    classification varchar(32),
    declared_access varchar(32),
    content_sha256 varchar(64),
    status varchar(32) NOT NULL,
    issue_code varchar(64),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_normalized_raw_version UNIQUE (raw_source_object_id, normalizer_version),
    CONSTRAINT uq_normalized_chain UNIQUE (
        id,
        organization_id,
        raw_source_object_id,
        source_acl_snapshot_id
    ),
    CONSTRAINT fk_normalized_snapshot_chain
        FOREIGN KEY (source_acl_snapshot_id, organization_id, raw_source_object_id)
        REFERENCES source_acl_snapshots(id, organization_id, raw_source_object_id),
    CONSTRAINT fk_normalized_snapshot_seal
        FOREIGN KEY (source_acl_snapshot_id, organization_id)
        REFERENCES source_acl_snapshot_seals(source_acl_snapshot_id, organization_id),
    CONSTRAINT fk_normalized_department_organization
        FOREIGN KEY (department_id, organization_id)
        REFERENCES departments(id, organization_id),
    CONSTRAINT chk_normalized_version CHECK (btrim(normalizer_version) <> ''),
    CONSTRAINT chk_normalized_status
        CHECK (status IN ('READY', 'QUARANTINED', 'PROMOTED', 'REJECTED')),
    CONSTRAINT chk_normalized_issue CHECK (
        issue_code IS NULL
        OR issue_code IN (
            'CONTENT_EMPTY',
            'CLASSIFICATION_MISSING',
            'DECLARED_ACCESS_MISSING',
            'DECLARED_ACCESS_MISMATCH',
            'DEPARTMENT_MISSING',
            'ACL_NOT_COMPLETE'
        )
    ),
    CONSTRAINT chk_normalized_content_sha
        CHECK (content_sha256 IS NULL OR content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_normalized_ready_fields CHECK (
        (
            status IN ('READY', 'PROMOTED')
            AND title IS NOT NULL AND btrim(title) <> ''
            AND normalized_content IS NOT NULL AND btrim(normalized_content) <> ''
            AND content_sha256 IS NOT NULL
            AND classification IS NOT NULL
            AND declared_access IS NOT NULL
            AND issue_code IS NULL
        )
        OR (status = 'QUARANTINED' AND issue_code IS NOT NULL)
        OR status = 'REJECTED'
    ),
    CONSTRAINT chk_normalized_classification_access CHECK (
        status NOT IN ('READY', 'PROMOTED')
        OR (
            (classification = 'PUBLIC' AND declared_access = 'ALL')
            OR (classification = 'INTERNAL' AND declared_access = 'ALL_EMPLOYEES')
            OR (classification = 'CONFIDENTIAL' AND declared_access = 'OWN_DEPARTMENT')
            OR (classification = 'RESTRICTED' AND declared_access = 'EXECUTIVE_ONLY')
        )
    ),
    CONSTRAINT chk_normalized_confidential_department CHECK (
        status NOT IN ('READY', 'PROMOTED')
        OR classification <> 'CONFIDENTIAL'
        OR department_id IS NOT NULL
    )
);

CREATE TABLE knowledge_assets (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    raw_source_object_id uuid NOT NULL,
    normalized_record_id uuid NOT NULL,
    source_acl_snapshot_id uuid NOT NULL,
    department_id uuid,
    title varchar(255) NOT NULL,
    content text NOT NULL,
    language varchar(16),
    classification varchar(32) NOT NULL,
    declared_access varchar(32) NOT NULL,
    content_sha256 varchar(64) NOT NULL,
    orgmemory_gate varchar(16) NOT NULL,
    status varchar(32) NOT NULL,
    activated_at timestamptz NOT NULL,
    retired_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_knowledge_normalized UNIQUE (normalized_record_id),
    CONSTRAINT uq_knowledge_id_organization UNIQUE (id, organization_id),
    CONSTRAINT fk_knowledge_normalized_chain
        FOREIGN KEY (
            normalized_record_id,
            organization_id,
            raw_source_object_id,
            source_acl_snapshot_id
        )
        REFERENCES normalized_records(
            id,
            organization_id,
            raw_source_object_id,
            source_acl_snapshot_id
        ),
    CONSTRAINT fk_knowledge_department_organization
        FOREIGN KEY (department_id, organization_id)
        REFERENCES departments(id, organization_id),
    CONSTRAINT chk_knowledge_nonblank CHECK (btrim(title) <> '' AND btrim(content) <> ''),
    CONSTRAINT chk_knowledge_content_sha CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_knowledge_classification_access CHECK (
        (classification = 'PUBLIC' AND declared_access = 'ALL')
        OR (classification = 'INTERNAL' AND declared_access = 'ALL_EMPLOYEES')
        OR (classification = 'CONFIDENTIAL' AND declared_access = 'OWN_DEPARTMENT')
        OR (classification = 'RESTRICTED' AND declared_access = 'EXECUTIVE_ONLY')
    ),
    CONSTRAINT chk_knowledge_confidential_department CHECK (
        classification <> 'CONFIDENTIAL' OR department_id IS NOT NULL
    ),
    CONSTRAINT chk_knowledge_orgmemory_gate
        CHECK (orgmemory_gate IN ('ALLOW', 'DENY', 'UNKNOWN')),
    CONSTRAINT chk_knowledge_status CHECK (status IN ('ACTIVE', 'RETIRED')),
    CONSTRAINT chk_knowledge_retired_at CHECK (
        (status = 'ACTIVE' AND retired_at IS NULL)
        OR (status = 'RETIRED' AND retired_at IS NOT NULL)
    )
);

CREATE INDEX idx_raw_source_lookup
    ON raw_source_objects (organization_id, source_system, source_connection_key, external_object_id);
CREATE INDEX idx_raw_source_status ON raw_source_objects (organization_id, status);
CREATE INDEX idx_source_acl_principal
    ON source_acl_entries (organization_id, principal_type, principal_key, source_acl_snapshot_id);
CREATE INDEX idx_source_acl_head_current
    ON source_acl_heads (organization_id, current_snapshot_id);
CREATE INDEX idx_source_acl_seal_organization
    ON source_acl_snapshot_seals (organization_id, source_acl_snapshot_id);
CREATE INDEX idx_normalized_status ON normalized_records (organization_id, status);
CREATE INDEX idx_knowledge_status ON knowledge_assets (organization_id, status, updated_at DESC);
CREATE INDEX idx_knowledge_classification ON knowledge_assets (organization_id, classification);
CREATE INDEX idx_knowledge_department ON knowledge_assets (organization_id, department_id);
