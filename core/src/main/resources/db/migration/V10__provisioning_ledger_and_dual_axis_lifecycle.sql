ALTER TABLE public.app_users
    ADD COLUMN local_access_enabled boolean,
    ADD COLUMN directory_access_enabled boolean,
    ADD COLUMN provisioning_access_ready boolean;

UPDATE public.app_users
SET local_access_enabled = active,
    directory_access_enabled = NULL,
    provisioning_access_ready = true;

ALTER TABLE public.app_users
    ALTER COLUMN local_access_enabled SET DEFAULT true,
    ALTER COLUMN local_access_enabled SET NOT NULL,
    ALTER COLUMN provisioning_access_ready SET DEFAULT true,
    ALTER COLUMN provisioning_access_ready SET NOT NULL;

CREATE FUNCTION public.materialize_app_user_active() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        -- An old binary's inactive insert supplies only active=false while the
        -- new columns take their defaults. New binaries supply a non-default
        -- axis when directory/readiness caused effective inactivity.
        IF NOT NEW.active
           AND NEW.local_access_enabled
           AND NEW.directory_access_enabled IS NULL
           AND NEW.provisioning_access_ready THEN
            NEW.local_access_enabled := false;
        END IF;
    ELSIF NEW.active IS DISTINCT FROM OLD.active
          AND NEW.local_access_enabled IS NOT DISTINCT FROM OLD.local_access_enabled
          AND NEW.directory_access_enabled IS NOT DISTINCT FROM OLD.directory_access_enabled
          AND NEW.provisioning_access_ready IS NOT DISTINCT FROM OLD.provisioning_access_ready THEN
        -- Interpret a previous binary's update as a local administrator decision.
        NEW.local_access_enabled := NEW.active;
    END IF;

    NEW.active := NEW.local_access_enabled
        AND COALESCE(NEW.directory_access_enabled, true)
        AND NEW.provisioning_access_ready;
    RETURN NEW;
END
$$;

CREATE TRIGGER app_users_materialize_active
    BEFORE INSERT OR UPDATE OF active, local_access_enabled,
        directory_access_enabled, provisioning_access_ready
    ON public.app_users
    FOR EACH ROW
    EXECUTE FUNCTION public.materialize_app_user_active();

CREATE TABLE public.provisioning_connections (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    alias character varying(128) NOT NULL,
    provider_profile character varying(32) NOT NULL,
    configuration_status character varying(32) NOT NULL,
    operational_state character varying(32) NOT NULL,
    users_enabled boolean NOT NULL,
    groups_enabled boolean NOT NULL,
    keycloak_realm character varying(128),
    keycloak_client_id character varying(255),
    keycloak_idp_alias character varying(128),
    mapper_fingerprint character varying(128),
    correlation_probe_status character varying(32) NOT NULL,
    validated_at timestamp with time zone,
    enabled_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT provisioning_connections_pkey PRIMARY KEY (id),
    CONSTRAINT uq_provisioning_connection_id_organization
        UNIQUE (id, organization_id),
    CONSTRAINT chk_provisioning_connection_alias
        CHECK (btrim(alias) <> ''),
    CONSTRAINT chk_provisioning_connection_provider
        CHECK (provider_profile IN ('GENERIC_SCIM', 'MICROSOFT_ENTRA', 'OKTA')),
    CONSTRAINT chk_provisioning_connection_configuration
        CHECK (configuration_status IN ('DRAFT', 'VALIDATED')),
    CONSTRAINT chk_provisioning_connection_operational
        CHECK (operational_state IN ('DISABLED', 'VALIDATING', 'ENABLED', 'READ_ONLY', 'SUSPENDED')),
    CONSTRAINT chk_provisioning_connection_correlation
        CHECK (correlation_probe_status IN ('NOT_RUN', 'PASSED', 'FAILED')),
    CONSTRAINT provisioning_connections_organization_id_fkey
        FOREIGN KEY (organization_id) REFERENCES public.organizations(id)
);

CREATE UNIQUE INDEX uq_provisioning_connection_correlation_active
    ON public.provisioning_connections (organization_id)
    WHERE operational_state IN ('VALIDATING', 'ENABLED', 'READ_ONLY', 'SUSPENDED');

CREATE UNIQUE INDEX uq_provisioning_connection_alias_lower
    ON public.provisioning_connections (organization_id, lower(alias));

CREATE TABLE public.provisioning_credentials (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    connection_id uuid NOT NULL,
    public_token_id character varying(64) NOT NULL,
    verifier_digest character varying(43) NOT NULL,
    verifier_key_version integer NOT NULL,
    users_scope boolean NOT NULL,
    groups_scope boolean NOT NULL,
    expires_at timestamp with time zone,
    overlap_ends_at timestamp with time zone,
    revoked_at timestamp with time zone,
    last_used_at timestamp with time zone,
    created_by_user_id uuid NOT NULL,
    revoked_by_user_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT provisioning_credentials_pkey PRIMARY KEY (id),
    CONSTRAINT uq_provisioning_credential_public_token UNIQUE (public_token_id),
    CONSTRAINT uq_provisioning_credential_id_organization
        UNIQUE (id, organization_id),
    CONSTRAINT chk_provisioning_credential_public_token
        CHECK (btrim(public_token_id) <> ''),
    CONSTRAINT chk_provisioning_credential_digest
        CHECK (verifier_digest ~ '^[A-Za-z0-9_-]{43}$'),
    CONSTRAINT chk_provisioning_credential_key_version
        CHECK (verifier_key_version > 0),
    CONSTRAINT fk_provisioning_credential_connection
        FOREIGN KEY (connection_id, organization_id)
        REFERENCES public.provisioning_connections(id, organization_id),
    CONSTRAINT fk_provisioning_credential_created_by
        FOREIGN KEY (created_by_user_id, organization_id)
        REFERENCES public.app_users(id, organization_id),
    CONSTRAINT fk_provisioning_credential_revoked_by
        FOREIGN KEY (revoked_by_user_id, organization_id)
        REFERENCES public.app_users(id, organization_id)
);

CREATE TABLE public.scim_user_resources (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    connection_id uuid NOT NULL,
    app_user_id uuid,
    external_id character varying(255),
    normalized_user_name character varying(320) NOT NULL,
    normalized_email character varying(320),
    workforce_key character varying(255),
    display_name character varying(255),
    given_name character varying(255),
    family_name character varying(255),
    directory_active boolean NOT NULL,
    tombstoned_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT scim_user_resources_pkey PRIMARY KEY (id),
    CONSTRAINT uq_scim_user_resource_id_organization
        UNIQUE (id, organization_id),
    CONSTRAINT uq_scim_user_external_id
        UNIQUE (organization_id, connection_id, external_id),
    CONSTRAINT uq_scim_user_name
        UNIQUE (organization_id, connection_id, normalized_user_name),
    CONSTRAINT uq_scim_user_workforce_key
        UNIQUE (organization_id, connection_id, workforce_key),
    CONSTRAINT uq_scim_user_app_user
        UNIQUE (organization_id, connection_id, app_user_id),
    CONSTRAINT chk_scim_user_name
        CHECK (btrim(normalized_user_name) <> ''),
    CONSTRAINT chk_scim_user_tombstone
        CHECK ((tombstoned_at IS NULL) OR (NOT directory_active)),
    CONSTRAINT fk_scim_user_connection
        FOREIGN KEY (connection_id, organization_id)
        REFERENCES public.provisioning_connections(id, organization_id),
    CONSTRAINT fk_scim_user_app_user
        FOREIGN KEY (app_user_id, organization_id)
        REFERENCES public.app_users(id, organization_id),
    CONSTRAINT scim_user_resources_organization_id_fkey
        FOREIGN KEY (organization_id) REFERENCES public.organizations(id)
);

CREATE TABLE public.provisioning_events (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    connection_id uuid NOT NULL,
    resource_id uuid,
    public_token_id character varying(64),
    request_id character varying(128) NOT NULL,
    operation character varying(32) NOT NULL,
    outcome character varying(32) NOT NULL,
    reason_code character varying(64),
    changed_fields character varying(1024) NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT provisioning_events_pkey PRIMARY KEY (id),
    CONSTRAINT chk_provisioning_event_request_id CHECK (btrim(request_id) <> ''),
    CONSTRAINT chk_provisioning_event_operation
        CHECK (operation IN ('CREATE', 'REPLACE', 'PATCH', 'DELETE', 'AUTHENTICATE', 'ROTATE', 'STATE_CHANGE')),
    CONSTRAINT chk_provisioning_event_outcome
        CHECK (outcome IN ('SUCCEEDED', 'DENIED', 'FAILED', 'NO_CHANGE')),
    CONSTRAINT fk_provisioning_event_connection
        FOREIGN KEY (connection_id, organization_id)
        REFERENCES public.provisioning_connections(id, organization_id),
    CONSTRAINT provisioning_events_organization_id_fkey
        FOREIGN KEY (organization_id) REFERENCES public.organizations(id)
);

CREATE INDEX ix_provisioning_events_tenant_time
    ON public.provisioning_events (organization_id, occurred_at DESC);

CREATE FUNCTION public.reject_provisioning_event_mutation() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'provisioning events are append-only';
END
$$;

CREATE TRIGGER provisioning_events_append_only
    BEFORE DELETE OR UPDATE OR TRUNCATE ON public.provisioning_events
    FOR EACH STATEMENT EXECUTE FUNCTION public.reject_provisioning_event_mutation();

ALTER TABLE public.provisioning_events
    ENABLE ALWAYS TRIGGER provisioning_events_append_only;
