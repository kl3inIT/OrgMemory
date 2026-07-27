ALTER TABLE source_principals
    RENAME COLUMN external_key TO native_principal_id;

ALTER TABLE source_principals
    DROP CONSTRAINT uq_source_principal_identity;

ALTER TABLE source_principals
    ADD CONSTRAINT uq_source_principal_identity
        UNIQUE (
            organization_id,
            source_system,
            source_connection_key,
            kind,
            native_principal_id
        );

DROP TABLE source_acl_group_members;

CREATE TABLE source_membership_sync_runs (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    source_system varchar(64) NOT NULL,
    source_connection_key varchar(128) NOT NULL,
    captured_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT chk_source_membership_sync_run_nonblank CHECK (
        btrim(source_system) <> ''
        AND btrim(source_connection_key) <> ''
    ),
    CONSTRAINT uq_source_membership_sync_run_id_organization
        UNIQUE (id, organization_id),
    CONSTRAINT fk_source_membership_sync_run_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id)
);

CREATE TABLE source_group_membership_snapshots (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    sync_run_id uuid NOT NULL,
    group_principal_id uuid NOT NULL,
    group_principal_kind varchar(16) NOT NULL DEFAULT 'SOURCE_GROUP',
    membership_generation bigint NOT NULL,
    capture_status varchar(16) NOT NULL,
    incomplete_reason varchar(128),
    captured_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT chk_source_group_membership_group_kind
        CHECK (group_principal_kind = 'SOURCE_GROUP'),
    CONSTRAINT chk_source_group_membership_generation
        CHECK (membership_generation > 0),
    CONSTRAINT chk_source_group_membership_capture_status
        CHECK (capture_status IN ('COMPLETE', 'INCOMPLETE')),
    CONSTRAINT chk_source_group_membership_completeness CHECK (
        (capture_status = 'COMPLETE' AND incomplete_reason IS NULL)
        OR
        (
            capture_status = 'INCOMPLETE'
            AND incomplete_reason IS NOT NULL
            AND btrim(incomplete_reason) <> ''
        )
    ),
    CONSTRAINT uq_source_group_membership_snapshot_id_organization
        UNIQUE (id, organization_id),
    CONSTRAINT uq_source_group_membership_snapshot_complete_identity
        UNIQUE (
            id,
            organization_id,
            group_principal_id,
            membership_generation,
            capture_status
        ),
    CONSTRAINT uq_source_group_membership_generation
        UNIQUE (organization_id, group_principal_id, membership_generation),
    CONSTRAINT fk_source_group_membership_snapshot_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_source_group_membership_snapshot_run
        FOREIGN KEY (sync_run_id, organization_id)
        REFERENCES source_membership_sync_runs(id, organization_id),
    CONSTRAINT fk_source_group_membership_snapshot_group
        FOREIGN KEY (group_principal_id, organization_id, group_principal_kind)
        REFERENCES source_principals(id, organization_id, kind)
);

CREATE TABLE source_group_membership_members (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    membership_snapshot_id uuid NOT NULL,
    member_principal_id uuid NOT NULL,
    member_principal_kind varchar(16) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT chk_source_group_membership_member_kind
        CHECK (member_principal_kind IN ('SOURCE_USER', 'SOURCE_GROUP')),
    CONSTRAINT uq_source_group_membership_member
        UNIQUE (membership_snapshot_id, member_principal_id),
    CONSTRAINT fk_source_group_membership_member_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_source_group_membership_member_snapshot
        FOREIGN KEY (membership_snapshot_id, organization_id)
        REFERENCES source_group_membership_snapshots(id, organization_id),
    CONSTRAINT fk_source_group_membership_member_principal
        FOREIGN KEY (member_principal_id, organization_id, member_principal_kind)
        REFERENCES source_principals(id, organization_id, kind)
);

CREATE TABLE source_group_membership_snapshot_seals (
    membership_snapshot_id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    group_principal_id uuid NOT NULL,
    membership_generation bigint NOT NULL,
    capture_status varchar(16) NOT NULL DEFAULT 'COMPLETE',
    member_count integer NOT NULL,
    members_sha256 varchar(64) NOT NULL,
    sealed_at timestamptz NOT NULL,
    CONSTRAINT chk_source_group_membership_seal_complete
        CHECK (capture_status = 'COMPLETE'),
    CONSTRAINT chk_source_group_membership_seal_count
        CHECK (member_count >= 0),
    CONSTRAINT chk_source_group_membership_seal_sha
        CHECK (members_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT uq_source_group_membership_seal_id_organization
        UNIQUE (membership_snapshot_id, organization_id),
    CONSTRAINT fk_source_group_membership_seal_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_source_group_membership_seal_snapshot
        FOREIGN KEY (
            membership_snapshot_id,
            organization_id,
            group_principal_id,
            membership_generation,
            capture_status
        )
        REFERENCES source_group_membership_snapshots(
            id,
            organization_id,
            group_principal_id,
            membership_generation,
            capture_status
        )
);

CREATE TABLE source_group_membership_heads (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    group_principal_id uuid NOT NULL,
    group_principal_kind varchar(16) NOT NULL DEFAULT 'SOURCE_GROUP',
    current_snapshot_id uuid NOT NULL,
    membership_generation bigint NOT NULL,
    capture_status varchar(16) NOT NULL DEFAULT 'COMPLETE',
    activated_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT chk_source_group_membership_head_group_kind
        CHECK (group_principal_kind = 'SOURCE_GROUP'),
    CONSTRAINT chk_source_group_membership_head_complete
        CHECK (capture_status = 'COMPLETE'),
    CONSTRAINT chk_source_group_membership_head_generation
        CHECK (membership_generation > 0),
    CONSTRAINT uq_source_group_membership_head_group
        UNIQUE (organization_id, group_principal_id),
    CONSTRAINT fk_source_group_membership_head_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_source_group_membership_head_group
        FOREIGN KEY (group_principal_id, organization_id, group_principal_kind)
        REFERENCES source_principals(id, organization_id, kind),
    CONSTRAINT fk_source_group_membership_head_snapshot
        FOREIGN KEY (
            current_snapshot_id,
            organization_id,
            group_principal_id,
            membership_generation,
            capture_status
        )
        REFERENCES source_group_membership_snapshots(
            id,
            organization_id,
            group_principal_id,
            membership_generation,
            capture_status
        ),
    CONSTRAINT fk_source_group_membership_head_seal
        FOREIGN KEY (current_snapshot_id, organization_id)
        REFERENCES source_group_membership_snapshot_seals(
            membership_snapshot_id,
            organization_id
        )
);

CREATE INDEX idx_source_group_membership_member_principal
    ON source_group_membership_members (organization_id, member_principal_id);

CREATE INDEX idx_source_group_membership_snapshot_group
    ON source_group_membership_snapshots (
        organization_id,
        group_principal_id,
        membership_generation DESC
    );

CREATE INDEX idx_source_group_membership_head_snapshot
    ON source_group_membership_heads (organization_id, current_snapshot_id);

CREATE FUNCTION reject_member_insert_into_sealed_membership() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM source_group_membership_snapshot_seals seal
        WHERE seal.membership_snapshot_id = NEW.membership_snapshot_id
          AND seal.organization_id = NEW.organization_id
    ) THEN
        RAISE EXCEPTION 'source group membership snapshot is sealed'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER source_membership_sync_runs_append_only
    BEFORE DELETE OR UPDATE OR TRUNCATE ON source_membership_sync_runs
    FOR EACH STATEMENT EXECUTE FUNCTION reject_source_acl_evidence_mutation();

CREATE TRIGGER source_group_membership_snapshots_append_only
    BEFORE DELETE OR UPDATE OR TRUNCATE ON source_group_membership_snapshots
    FOR EACH STATEMENT EXECUTE FUNCTION reject_source_acl_evidence_mutation();

CREATE TRIGGER source_group_membership_members_append_only
    BEFORE DELETE OR UPDATE OR TRUNCATE ON source_group_membership_members
    FOR EACH STATEMENT EXECUTE FUNCTION reject_source_acl_evidence_mutation();

CREATE TRIGGER source_group_membership_members_reject_after_seal
    BEFORE INSERT ON source_group_membership_members
    FOR EACH ROW EXECUTE FUNCTION reject_member_insert_into_sealed_membership();

CREATE TRIGGER source_group_membership_seals_append_only
    BEFORE DELETE OR UPDATE OR TRUNCATE ON source_group_membership_snapshot_seals
    FOR EACH STATEMENT EXECUTE FUNCTION reject_source_acl_evidence_mutation();

COMMENT ON TABLE source_membership_sync_runs IS
    'One immutable connector membership-capture boundary. Technical failure rolls back the run; incomplete source evidence is retained on its snapshots.';

COMMENT ON TABLE source_group_membership_snapshots IS
    'Immutable per-group membership evidence independent from resource ACL snapshots. Only sealed COMPLETE snapshots may become active.';

COMMENT ON TABLE source_group_membership_heads IS
    'Atomic pointer to the current sealed COMPLETE membership snapshot for one source group.';
