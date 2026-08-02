ALTER TABLE knowledge_spaces
    ADD COLUMN audience_mode varchar(32),
    ADD COLUMN audience_version bigint;

UPDATE knowledge_spaces
SET audience_mode = CASE
        WHEN department_id IS NULL THEN 'ORGANIZATION'
        ELSE 'DEPARTMENT'
    END,
    audience_version = 1;

ALTER TABLE knowledge_spaces
    ALTER COLUMN audience_mode SET NOT NULL,
    ALTER COLUMN audience_version SET NOT NULL,
    ADD CONSTRAINT chk_knowledge_space_audience_mode
        CHECK (audience_mode IN ('ORGANIZATION', 'DEPARTMENT', 'RESTRICTED_CUSTOM')),
    ADD CONSTRAINT chk_knowledge_space_audience_version
        CHECK (audience_version > 0),
    ADD CONSTRAINT chk_knowledge_space_audience_department
        CHECK (
            (audience_mode = 'DEPARTMENT' AND department_id IS NOT NULL)
            OR (audience_mode IN ('ORGANIZATION', 'RESTRICTED_CUSTOM') AND department_id IS NULL)
        );

CREATE TABLE knowledge_space_custom_viewer_grants (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    knowledge_space_id uuid NOT NULL,
    subject_kind varchar(32) NOT NULL,
    user_id uuid,
    department_id uuid,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT chk_space_custom_viewer_subject CHECK (
        (subject_kind = 'USER' AND user_id IS NOT NULL AND department_id IS NULL)
        OR (subject_kind = 'DEPARTMENT' AND department_id IS NOT NULL AND user_id IS NULL)
    ),
    CONSTRAINT uq_space_custom_viewer_subject
        UNIQUE NULLS NOT DISTINCT (
            organization_id, knowledge_space_id, subject_kind, user_id, department_id),
    CONSTRAINT fk_space_custom_viewer_space
        FOREIGN KEY (knowledge_space_id, organization_id)
        REFERENCES knowledge_spaces (id, organization_id),
    CONSTRAINT fk_space_custom_viewer_user
        FOREIGN KEY (user_id, organization_id)
        REFERENCES app_users (id, organization_id),
    CONSTRAINT fk_space_custom_viewer_department
        FOREIGN KEY (department_id, organization_id)
        REFERENCES departments (id, organization_id)
);
