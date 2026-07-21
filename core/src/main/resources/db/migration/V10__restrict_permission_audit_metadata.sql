ALTER TABLE permission_audit_events
    ADD CONSTRAINT chk_permission_audit_metadata_json_null
    CHECK (metadata_json IS NULL);

COMMENT ON COLUMN permission_audit_events.metadata_json IS
    'Reserved. Free-form audit metadata is disabled until a structured allowlist is defined.';
