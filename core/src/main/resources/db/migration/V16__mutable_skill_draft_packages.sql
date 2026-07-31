CREATE OR REPLACE FUNCTION public.guard_asset_payload_reference_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' AND OLD.owner_kind = 'DRAFT' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION '% is immutable for owner kind %', TG_TABLE_NAME, OLD.owner_kind;
END;
$$;

DROP TRIGGER reject_asset_payload_reference_mutation
    ON public.asset_payload_references;

CREATE TRIGGER reject_asset_payload_reference_mutation
    BEFORE UPDATE OR DELETE ON public.asset_payload_references
    FOR EACH ROW EXECUTE FUNCTION public.guard_asset_payload_reference_mutation();

CREATE TABLE public.skill_package_supersessions (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    superseded_reference_value varchar(1024) NOT NULL,
    replacement_reference_value varchar(1024) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamp with time zone NOT NULL,
    last_error_code varchar(64),
    last_error_message varchar(512),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT skill_package_supersessions_pkey PRIMARY KEY (id),
    CONSTRAINT skill_package_supersession_attempt_check CHECK (attempt_count >= 0),
    CONSTRAINT skill_package_supersession_distinct_check CHECK (
        superseded_reference_value <> replacement_reference_value
    ),
    CONSTRAINT uq_skill_package_supersession_reference UNIQUE (
        organization_id, superseded_reference_value
    ),
    CONSTRAINT fk_skill_package_supersession_asset
        FOREIGN KEY (asset_id, organization_id)
        REFERENCES public.assets(id, organization_id)
);

CREATE INDEX idx_skill_package_supersession_retry
    ON public.skill_package_supersessions (
        next_attempt_at, attempt_count, created_at
    );
