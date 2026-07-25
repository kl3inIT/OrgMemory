CREATE TABLE public.prompt_runs (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    release_id uuid NOT NULL,
    release_digest varchar(64) NOT NULL,
    actor_user_id uuid NOT NULL,
    gateway_id varchar(64) NOT NULL,
    model_id varchar(256) NOT NULL,
    input_shape_digest varchar(64) NOT NULL,
    citation_refs jsonb NOT NULL,
    status varchar(16) NOT NULL,
    duration_millis bigint,
    sanitized_outcome jsonb,
    error_code varchar(64),
    started_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT prompt_runs_pkey PRIMARY KEY (id),
    CONSTRAINT prompt_runs_status_check CHECK (
        status IN ('RUNNING', 'SUCCEEDED', 'FAILED')
    ),
    CONSTRAINT prompt_runs_citations_array_check CHECK (
        jsonb_typeof(citation_refs) = 'array'
    ),
    CONSTRAINT prompt_runs_completion_check CHECK (
        (status = 'RUNNING' AND completed_at IS NULL AND duration_millis IS NULL)
        OR
        (status <> 'RUNNING' AND completed_at IS NOT NULL AND duration_millis IS NOT NULL)
    ),
    CONSTRAINT fk_prompt_run_release FOREIGN KEY (
        release_id, asset_id, organization_id
    ) REFERENCES public.asset_releases(id, asset_id, organization_id),
    CONSTRAINT fk_prompt_run_actor FOREIGN KEY (actor_user_id, organization_id)
        REFERENCES public.app_users(id, organization_id)
);

CREATE INDEX idx_prompt_runs_asset_history
    ON public.prompt_runs (organization_id, asset_id, started_at DESC);

CREATE TABLE public.prompt_evaluation_runs (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    release_id uuid NOT NULL,
    release_digest varchar(64) NOT NULL,
    actor_user_id uuid NOT NULL,
    passed_cases integer NOT NULL,
    total_cases integer NOT NULL,
    details jsonb NOT NULL,
    evaluated_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT prompt_evaluation_runs_pkey PRIMARY KEY (id),
    CONSTRAINT prompt_evaluation_counts_check CHECK (
        total_cases > 0 AND passed_cases >= 0 AND passed_cases <= total_cases
    ),
    CONSTRAINT prompt_evaluation_details_array_check CHECK (
        jsonb_typeof(details) = 'array'
    ),
    CONSTRAINT fk_prompt_evaluation_release FOREIGN KEY (
        release_id, asset_id, organization_id
    ) REFERENCES public.asset_releases(id, asset_id, organization_id),
    CONSTRAINT fk_prompt_evaluation_actor FOREIGN KEY (actor_user_id, organization_id)
        REFERENCES public.app_users(id, organization_id)
);

CREATE INDEX idx_prompt_evaluation_release_history
    ON public.prompt_evaluation_runs (
        organization_id, release_id, evaluated_at DESC
    );

CREATE TABLE public.work_instruction_acknowledgements (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    release_id uuid NOT NULL,
    release_digest varchar(64) NOT NULL,
    actor_user_id uuid NOT NULL,
    acknowledged_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT work_instruction_acknowledgements_pkey PRIMARY KEY (id),
    CONSTRAINT uq_work_instruction_acknowledgement UNIQUE (
        organization_id, release_id, actor_user_id
    ),
    CONSTRAINT fk_work_instruction_release FOREIGN KEY (
        release_id, asset_id, organization_id
    ) REFERENCES public.asset_releases(id, asset_id, organization_id),
    CONSTRAINT fk_work_instruction_actor FOREIGN KEY (actor_user_id, organization_id)
        REFERENCES public.app_users(id, organization_id)
);

CREATE TABLE public.pack_assignments (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    pack_asset_id uuid NOT NULL,
    pack_release_id uuid NOT NULL,
    release_digest varchar(64) NOT NULL,
    actor_user_id uuid NOT NULL,
    status varchar(16) NOT NULL,
    started_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT pack_assignments_pkey PRIMARY KEY (id),
    CONSTRAINT uq_pack_assignment UNIQUE (
        organization_id, pack_release_id, actor_user_id
    ),
    CONSTRAINT pack_assignment_status_check CHECK (
        status IN ('IN_PROGRESS', 'COMPLETED')
    ),
    CONSTRAINT pack_assignment_completion_check CHECK (
        (status = 'IN_PROGRESS' AND completed_at IS NULL)
        OR
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
    ),
    CONSTRAINT fk_pack_assignment_release FOREIGN KEY (
        pack_release_id, pack_asset_id, organization_id
    ) REFERENCES public.asset_releases(id, asset_id, organization_id),
    CONSTRAINT fk_pack_assignment_actor FOREIGN KEY (actor_user_id, organization_id)
        REFERENCES public.app_users(id, organization_id),
    CONSTRAINT uq_pack_assignment_tenant_id UNIQUE (id, organization_id)
);

CREATE TABLE public.pack_progress (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    assignment_id uuid NOT NULL,
    item_key varchar(64) NOT NULL,
    completed boolean NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT pack_progress_pkey PRIMARY KEY (id),
    CONSTRAINT uq_pack_progress_item UNIQUE (
        organization_id, assignment_id, item_key
    ),
    CONSTRAINT pack_progress_completion_check CHECK (
        (completed AND completed_at IS NOT NULL)
        OR
        (NOT completed AND completed_at IS NULL)
    ),
    CONSTRAINT fk_pack_progress_assignment FOREIGN KEY (
        assignment_id, organization_id
    ) REFERENCES public.pack_assignments(id, organization_id)
);

CREATE INDEX idx_pack_progress_assignment
    ON public.pack_progress (organization_id, assignment_id, item_key);

CREATE TRIGGER reject_prompt_evaluation_mutation
    BEFORE UPDATE OR DELETE ON public.prompt_evaluation_runs
    FOR EACH ROW EXECUTE FUNCTION public.reject_asset_immutable_mutation();
CREATE TRIGGER reject_work_instruction_acknowledgement_mutation
    BEFORE UPDATE OR DELETE ON public.work_instruction_acknowledgements
    FOR EACH ROW EXECUTE FUNCTION public.reject_asset_immutable_mutation();
