CREATE TABLE permission_audit_events (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    actor_user_id uuid,
    operation varchar(64) NOT NULL,
    resource_type varchar(64) NOT NULL,
    resource_id varchar(255) NOT NULL,
    decision varchar(16) NOT NULL CHECK (decision IN ('ALLOW', 'DENY')),
    reason_code varchar(128) NOT NULL,
    policy_version varchar(64) NOT NULL,
    request_id varchar(128),
    query_fingerprint varchar(64),
    metadata_json text,
    occurred_at timestamptz NOT NULL
);

CREATE INDEX idx_permission_audit_org_time
    ON permission_audit_events (organization_id, occurred_at DESC);

CREATE INDEX idx_permission_audit_actor_time
    ON permission_audit_events (organization_id, actor_user_id, occurred_at DESC);

CREATE INDEX idx_permission_audit_resource
    ON permission_audit_events (organization_id, resource_type, resource_id);

CREATE FUNCTION reject_permission_audit_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'permission_audit_events is append-only'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER permission_audit_events_append_only
BEFORE UPDATE OR DELETE OR TRUNCATE
ON permission_audit_events
FOR EACH STATEMENT
EXECUTE FUNCTION reject_permission_audit_mutation();

ALTER TABLE permission_audit_events
    ENABLE ALWAYS TRIGGER permission_audit_events_append_only;
