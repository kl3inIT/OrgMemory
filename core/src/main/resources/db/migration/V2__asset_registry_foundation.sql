CREATE FUNCTION public.reject_asset_immutable_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION '% is immutable', TG_TABLE_NAME
        USING ERRCODE = '55000';
END;
$$;

CREATE TABLE public.assets (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    asset_type varchar(64) NOT NULL,
    namespace varchar(128) NOT NULL,
    slug varchar(128) NOT NULL,
    knowledge_space_id uuid NOT NULL,
    portfolio_state varchar(32) NOT NULL,
    authorization_ready boolean NOT NULL DEFAULT false,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT assets_pkey PRIMARY KEY (id),
    CONSTRAINT assets_type_check CHECK (asset_type IN (
        'PROMPT_TEMPLATE', 'WORK_INSTRUCTION', 'CAPABILITY_PACK'
    )),
    CONSTRAINT assets_portfolio_state_check CHECK (portfolio_state IN (
        'DRAFT_ONLY', 'ACTIVE', 'SUNSETTING', 'RETIRED'
    )),
    CONSTRAINT assets_namespace_check CHECK (namespace ~ '^[a-z0-9]+(?:[._-][a-z0-9]+)*$'),
    CONSTRAINT assets_slug_check CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
    CONSTRAINT uq_asset_id_organization UNIQUE (id, organization_id),
    CONSTRAINT uq_asset_coordinate UNIQUE (organization_id, namespace, slug),
    CONSTRAINT fk_asset_organization FOREIGN KEY (organization_id)
        REFERENCES public.organizations(id),
    CONSTRAINT fk_asset_space_organization FOREIGN KEY (knowledge_space_id, organization_id)
        REFERENCES public.knowledge_spaces(id, organization_id)
);

CREATE INDEX idx_assets_catalog
    ON public.assets (organization_id, authorization_ready, portfolio_state, asset_type);

CREATE TABLE public.asset_role_assignments (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    principal_type varchar(32) NOT NULL,
    principal_id varchar(128) NOT NULL,
    role varchar(32) NOT NULL,
    valid_from timestamp with time zone NOT NULL,
    valid_until timestamp with time zone,
    assigned_by_user_id uuid NOT NULL,
    projected_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT asset_role_assignments_pkey PRIMARY KEY (id),
    CONSTRAINT asset_role_principal_type_check CHECK (
        principal_type IN ('user', 'group')
    ),
    CONSTRAINT asset_role_check CHECK (role IN (
        'OWNER', 'BACKUP_OWNER', 'STEWARD', 'VIEWER',
        'EDITOR', 'REVIEWER', 'PUBLISHER'
    )),
    CONSTRAINT asset_role_window_check CHECK (
        valid_until IS NULL OR valid_until > valid_from
    ),
    CONSTRAINT uq_asset_role_id_asset_organization
        UNIQUE (id, asset_id, organization_id),
    CONSTRAINT fk_asset_role_asset FOREIGN KEY (asset_id, organization_id)
        REFERENCES public.assets(id, organization_id),
    CONSTRAINT fk_asset_role_organization FOREIGN KEY (organization_id)
        REFERENCES public.organizations(id),
    CONSTRAINT fk_asset_role_assigner FOREIGN KEY (assigned_by_user_id, organization_id)
        REFERENCES public.app_users(id, organization_id)
);

CREATE UNIQUE INDEX uq_asset_active_role
    ON public.asset_role_assignments (asset_id, principal_type, principal_id, role)
    WHERE valid_until IS NULL;

CREATE INDEX idx_asset_role_history
    ON public.asset_role_assignments (organization_id, asset_id, valid_from);

CREATE TABLE public.asset_drafts (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    title varchar(256) NOT NULL,
    summary varchar(1024) NOT NULL,
    classification varchar(32) NOT NULL,
    schema_version varchar(32) NOT NULL,
    payload text NOT NULL,
    edited_by_user_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT asset_drafts_pkey PRIMARY KEY (id),
    CONSTRAINT uq_asset_draft UNIQUE (asset_id),
    CONSTRAINT asset_draft_payload_object_check CHECK (
        jsonb_typeof(payload::jsonb) = 'object'
    ),
    CONSTRAINT fk_asset_draft_asset FOREIGN KEY (asset_id, organization_id)
        REFERENCES public.assets(id, organization_id),
    CONSTRAINT fk_asset_draft_editor FOREIGN KEY (edited_by_user_id, organization_id)
        REFERENCES public.app_users(id, organization_id)
);

CREATE TABLE public.asset_revisions (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    sequence_number bigint NOT NULL,
    title varchar(256) NOT NULL,
    summary varchar(1024) NOT NULL,
    classification varchar(32) NOT NULL,
    schema_version varchar(32) NOT NULL,
    payload text NOT NULL,
    digest varchar(64) NOT NULL,
    change_note varchar(1024) NOT NULL,
    created_by_user_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT asset_revisions_pkey PRIMARY KEY (id),
    CONSTRAINT uq_asset_revision_sequence UNIQUE (asset_id, sequence_number),
    CONSTRAINT asset_revision_sequence_check CHECK (sequence_number > 0),
    CONSTRAINT asset_revision_digest_check CHECK (digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT asset_revision_payload_object_check CHECK (
        jsonb_typeof(payload::jsonb) = 'object'
    ),
    CONSTRAINT uq_asset_revision_id_organization
        UNIQUE (id, organization_id),
    CONSTRAINT uq_asset_revision_id_asset_organization
        UNIQUE (id, asset_id, organization_id),
    CONSTRAINT fk_asset_revision_asset FOREIGN KEY (asset_id, organization_id)
        REFERENCES public.assets(id, organization_id),
    CONSTRAINT fk_asset_revision_author FOREIGN KEY (created_by_user_id, organization_id)
        REFERENCES public.app_users(id, organization_id)
);

CREATE TABLE public.asset_review_cases (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    revision_id uuid NOT NULL,
    revision_digest varchar(64) NOT NULL,
    state varchar(32) NOT NULL,
    policy_version varchar(64) NOT NULL,
    requested_by_user_id uuid NOT NULL,
    resolved_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT asset_review_cases_pkey PRIMARY KEY (id),
    CONSTRAINT uq_asset_review_revision UNIQUE (revision_id),
    CONSTRAINT asset_review_state_check CHECK (state IN (
        'IN_REVIEW', 'CHANGES_REQUESTED', 'REJECTED', 'CANCELLED', 'APPROVED'
    )),
    CONSTRAINT uq_asset_review_id_asset_organization
        UNIQUE (id, asset_id, organization_id),
    CONSTRAINT fk_asset_review_asset FOREIGN KEY (asset_id, organization_id)
        REFERENCES public.assets(id, organization_id),
    CONSTRAINT fk_asset_review_revision
        FOREIGN KEY (revision_id, asset_id, organization_id)
        REFERENCES public.asset_revisions(id, asset_id, organization_id),
    CONSTRAINT fk_asset_review_requester FOREIGN KEY (requested_by_user_id, organization_id)
        REFERENCES public.app_users(id, organization_id)
);

CREATE TABLE public.asset_review_decisions (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    review_case_id uuid NOT NULL,
    revision_id uuid NOT NULL,
    revision_digest varchar(64) NOT NULL,
    reviewer_user_id uuid NOT NULL,
    decision varchar(32) NOT NULL,
    comment varchar(2048) NOT NULL,
    policy_version varchar(64) NOT NULL,
    decided_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT asset_review_decisions_pkey PRIMARY KEY (id),
    CONSTRAINT asset_review_decision_check CHECK (decision IN (
        'REQUEST_CHANGES', 'REJECT', 'APPROVE', 'CANCEL'
    )),
    CONSTRAINT fk_asset_decision_asset FOREIGN KEY (asset_id, organization_id)
        REFERENCES public.assets(id, organization_id),
    CONSTRAINT fk_asset_decision_case
        FOREIGN KEY (review_case_id, asset_id, organization_id)
        REFERENCES public.asset_review_cases(id, asset_id, organization_id),
    CONSTRAINT fk_asset_decision_revision
        FOREIGN KEY (revision_id, asset_id, organization_id)
        REFERENCES public.asset_revisions(id, asset_id, organization_id),
    CONSTRAINT fk_asset_decision_reviewer FOREIGN KEY (reviewer_user_id, organization_id)
        REFERENCES public.app_users(id, organization_id)
);

CREATE INDEX idx_asset_review_decision_case
    ON public.asset_review_decisions (review_case_id, decided_at);

CREATE TABLE public.asset_releases (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    revision_id uuid NOT NULL,
    release_sequence bigint NOT NULL,
    version_label varchar(64) NOT NULL,
    title varchar(256) NOT NULL,
    summary varchar(1024) NOT NULL,
    classification varchar(32) NOT NULL,
    schema_version varchar(32) NOT NULL,
    payload text NOT NULL,
    digest varchar(64) NOT NULL,
    released_by_user_id uuid NOT NULL,
    released_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT asset_releases_pkey PRIMARY KEY (id),
    CONSTRAINT uq_asset_release_revision UNIQUE (revision_id),
    CONSTRAINT uq_asset_release_sequence UNIQUE (asset_id, release_sequence),
    CONSTRAINT uq_asset_release_label UNIQUE (asset_id, version_label),
    CONSTRAINT asset_release_sequence_check CHECK (release_sequence > 0),
    CONSTRAINT asset_release_digest_check CHECK (digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT asset_release_payload_object_check CHECK (
        jsonb_typeof(payload::jsonb) = 'object'
    ),
    CONSTRAINT uq_asset_release_id_asset_organization
        UNIQUE (id, asset_id, organization_id),
    CONSTRAINT uq_asset_release_id_organization
        UNIQUE (id, organization_id),
    CONSTRAINT fk_asset_release_asset FOREIGN KEY (asset_id, organization_id)
        REFERENCES public.assets(id, organization_id),
    CONSTRAINT fk_asset_release_revision
        FOREIGN KEY (revision_id, asset_id, organization_id)
        REFERENCES public.asset_revisions(id, asset_id, organization_id),
    CONSTRAINT fk_asset_release_publisher FOREIGN KEY (released_by_user_id, organization_id)
        REFERENCES public.app_users(id, organization_id)
);

CREATE TABLE public.asset_release_availability_events (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    release_id uuid NOT NULL,
    availability varchar(32) NOT NULL,
    reason varchar(1024) NOT NULL,
    changed_by_user_id uuid NOT NULL,
    effective_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT asset_release_availability_events_pkey PRIMARY KEY (id),
    CONSTRAINT asset_release_availability_check CHECK (availability IN (
        'AVAILABLE', 'DEPRECATED', 'WITHDRAWN'
    )),
    CONSTRAINT fk_asset_availability_asset FOREIGN KEY (asset_id, organization_id)
        REFERENCES public.assets(id, organization_id),
    CONSTRAINT fk_asset_availability_release
        FOREIGN KEY (release_id, asset_id, organization_id)
        REFERENCES public.asset_releases(id, asset_id, organization_id),
    CONSTRAINT fk_asset_availability_actor FOREIGN KEY (changed_by_user_id, organization_id)
        REFERENCES public.app_users(id, organization_id)
);

CREATE INDEX idx_asset_release_availability
    ON public.asset_release_availability_events (release_id, effective_at DESC, created_at DESC);

CREATE TABLE public.asset_relations (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    source_release_id uuid NOT NULL,
    relation_type varchar(64) NOT NULL,
    target_kind varchar(32) NOT NULL,
    target_id uuid NOT NULL,
    target_version_id uuid NOT NULL,
    target_digest varchar(64) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT asset_relations_pkey PRIMARY KEY (id),
    CONSTRAINT asset_relation_target_check CHECK (
        target_kind IN ('ASSET_RELEASE', 'KNOWLEDGE_ASSET_VERSION')
    ),
    CONSTRAINT asset_relation_digest_check CHECK (target_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT uq_asset_release_relation UNIQUE (
        source_release_id, relation_type, target_kind, target_id, target_version_id
    ),
    CONSTRAINT fk_asset_relation_release
        FOREIGN KEY (source_release_id, organization_id)
        REFERENCES public.asset_releases(id, organization_id)
);

CREATE TABLE public.asset_payload_references (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    owner_kind varchar(32) NOT NULL,
    revision_id uuid,
    release_id uuid,
    reference_kind varchar(32) NOT NULL,
    reference_value varchar(1024) NOT NULL,
    digest varchar(64),
    media_type varchar(128),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT asset_payload_references_pkey PRIMARY KEY (id),
    CONSTRAINT asset_payload_owner_check CHECK (
        (owner_kind = 'REVISION' AND revision_id IS NOT NULL AND release_id IS NULL)
        OR
        (owner_kind = 'RELEASE' AND release_id IS NOT NULL AND revision_id IS NULL)
    ),
    CONSTRAINT asset_payload_reference_check CHECK (reference_kind IN ('INLINE', 'BLOB')),
    CONSTRAINT asset_payload_reference_digest_check CHECK (
        digest IS NULL OR digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT fk_asset_payload_revision
        FOREIGN KEY (revision_id, organization_id)
        REFERENCES public.asset_revisions(id, organization_id),
    CONSTRAINT fk_asset_payload_release
        FOREIGN KEY (release_id, organization_id)
        REFERENCES public.asset_releases(id, organization_id)
);

CREATE INDEX idx_asset_payload_reference_owner
    ON public.asset_payload_references (
        organization_id, owner_kind, revision_id, release_id
    );

CREATE TABLE public.asset_authorization_outbox (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    role_assignment_id uuid,
    tuple_user varchar(256) NOT NULL,
    tuple_relation varchar(64) NOT NULL,
    tuple_object varchar(256) NOT NULL,
    status varchar(32) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    claim_token uuid,
    lease_until timestamp with time zone,
    next_attempt_at timestamp with time zone NOT NULL,
    authorization_model_id varchar(256),
    last_error_code varchar(64),
    last_error_message varchar(512),
    applied_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT asset_authorization_outbox_pkey PRIMARY KEY (id),
    CONSTRAINT asset_authorization_outbox_status_check CHECK (
        status IN ('PENDING', 'IN_FLIGHT', 'DEAD_LETTER', 'APPLIED')
    ),
    CONSTRAINT asset_authorization_outbox_claim_check CHECK (
        (status = 'IN_FLIGHT' AND claim_token IS NOT NULL AND lease_until IS NOT NULL)
        OR
        (status <> 'IN_FLIGHT' AND claim_token IS NULL AND lease_until IS NULL)
    ),
    CONSTRAINT uq_asset_authorization_tuple UNIQUE (
        asset_id, tuple_user, tuple_relation, tuple_object
    ),
    CONSTRAINT fk_asset_authorization_asset FOREIGN KEY (asset_id, organization_id)
        REFERENCES public.assets(id, organization_id),
    CONSTRAINT fk_asset_authorization_role
        FOREIGN KEY (role_assignment_id, asset_id, organization_id)
        REFERENCES public.asset_role_assignments(id, asset_id, organization_id)
);

CREATE INDEX idx_asset_authorization_pending
    ON public.asset_authorization_outbox (
        status, next_attempt_at, lease_until, created_at
    );

CREATE TABLE public.asset_audit_events (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    event_type varchar(64) NOT NULL,
    subject_type varchar(64) NOT NULL,
    subject_id uuid NOT NULL,
    decision_context jsonb NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT asset_audit_events_pkey PRIMARY KEY (id),
    CONSTRAINT asset_audit_context_object_check CHECK (
        jsonb_typeof(decision_context) = 'object'
    ),
    CONSTRAINT fk_asset_audit_asset FOREIGN KEY (asset_id, organization_id)
        REFERENCES public.assets(id, organization_id),
    CONSTRAINT fk_asset_audit_actor FOREIGN KEY (actor_user_id, organization_id)
        REFERENCES public.app_users(id, organization_id)
);

CREATE INDEX idx_asset_audit_history
    ON public.asset_audit_events (organization_id, asset_id, occurred_at);

CREATE TRIGGER reject_asset_revision_mutation
    BEFORE UPDATE OR DELETE ON public.asset_revisions
    FOR EACH ROW EXECUTE FUNCTION public.reject_asset_immutable_mutation();
CREATE TRIGGER reject_asset_review_decision_mutation
    BEFORE UPDATE OR DELETE ON public.asset_review_decisions
    FOR EACH ROW EXECUTE FUNCTION public.reject_asset_immutable_mutation();
CREATE TRIGGER reject_asset_release_mutation
    BEFORE UPDATE OR DELETE ON public.asset_releases
    FOR EACH ROW EXECUTE FUNCTION public.reject_asset_immutable_mutation();
CREATE TRIGGER reject_asset_availability_mutation
    BEFORE UPDATE OR DELETE ON public.asset_release_availability_events
    FOR EACH ROW EXECUTE FUNCTION public.reject_asset_immutable_mutation();
CREATE TRIGGER reject_asset_relation_mutation
    BEFORE UPDATE OR DELETE ON public.asset_relations
    FOR EACH ROW EXECUTE FUNCTION public.reject_asset_immutable_mutation();
CREATE TRIGGER reject_asset_payload_reference_mutation
    BEFORE UPDATE OR DELETE ON public.asset_payload_references
    FOR EACH ROW EXECUTE FUNCTION public.reject_asset_immutable_mutation();
CREATE TRIGGER reject_asset_audit_mutation
    BEFORE UPDATE OR DELETE ON public.asset_audit_events
    FOR EACH ROW EXECUTE FUNCTION public.reject_asset_immutable_mutation();
