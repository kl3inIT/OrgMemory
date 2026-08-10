ALTER TABLE public.assets
    ADD COLUMN owner_user_id uuid,
    ADD COLUMN sharing_state varchar(32) NOT NULL DEFAULT 'PRIVATE',
    ADD COLUMN relationship_generation bigint NOT NULL DEFAULT 1,
    ADD COLUMN projected_relationship_generation bigint NOT NULL DEFAULT 0;

WITH ambiguous_assets AS (
    SELECT assignment.asset_id
    FROM public.asset_role_assignments assignment
    WHERE assignment.role = 'OWNER'
      AND assignment.valid_until IS NULL
    GROUP BY assignment.asset_id
    HAVING count(*) <> 1
)
UPDATE public.asset_role_assignments assignment
SET valid_until = greatest(
        CURRENT_TIMESTAMP,
        assignment.valid_from + interval '1 microsecond'
    ),
    updated_at = CURRENT_TIMESTAMP,
    version = assignment.version + 1
FROM ambiguous_assets ambiguous
WHERE assignment.asset_id = ambiguous.asset_id
  AND assignment.role = 'OWNER'
  AND assignment.valid_until IS NULL;

UPDATE public.assets asset
SET owner_user_id = owners.owner_user_id
FROM (
    SELECT assignment.asset_id,
           min(assignment.principal_id)::uuid AS owner_user_id
    FROM public.asset_role_assignments assignment
    WHERE assignment.role = 'OWNER'
      AND assignment.principal_type = 'user'
      AND assignment.valid_until IS NULL
    GROUP BY assignment.asset_id
    HAVING count(*) = 1
) owners
WHERE owners.asset_id = asset.id;

UPDATE public.assets asset
SET sharing_state = CASE
        WHEN EXISTS (
            SELECT 1
            FROM public.asset_role_assignments assignment
            WHERE assignment.asset_id = asset.id
              AND assignment.valid_until IS NULL
              AND assignment.role IN ('VIEWER', 'EDITOR')
              AND assignment.principal_type = 'organization'
        ) THEN 'ORGANIZATION'
        WHEN EXISTS (
            SELECT 1
            FROM public.asset_role_assignments assignment
            WHERE assignment.asset_id = asset.id
              AND assignment.valid_until IS NULL
              AND assignment.role IN ('VIEWER', 'EDITOR')
        ) THEN 'SHARED'
        ELSE 'PRIVATE'
    END,
    projected_relationship_generation = CASE
        WHEN asset.authorization_ready THEN 1
        ELSE 0
    END;

ALTER TABLE public.assets
    ADD CONSTRAINT assets_sharing_state_check CHECK (
        sharing_state IN ('PRIVATE', 'SHARED', 'ORGANIZATION')
    ),
    ADD CONSTRAINT assets_relationship_generation_check CHECK (
        relationship_generation > 0
        AND projected_relationship_generation >= 0
        AND projected_relationship_generation <= relationship_generation
    ),
    ADD CONSTRAINT fk_asset_owner FOREIGN KEY (owner_user_id, organization_id)
        REFERENCES public.app_users(id, organization_id);

CREATE UNIQUE INDEX uq_asset_single_active_owner
    ON public.asset_role_assignments (asset_id)
    WHERE valid_until IS NULL AND role = 'OWNER';

ALTER TABLE public.asset_role_assignments
    DROP CONSTRAINT asset_role_principal_type_check,
    ADD CONSTRAINT asset_role_principal_type_check CHECK (
        principal_type IN ('user', 'group', 'organization')
    );

ALTER TABLE public.asset_authorization_outbox
    DROP CONSTRAINT uq_asset_authorization_tuple,
    ADD COLUMN operation varchar(16) NOT NULL DEFAULT 'WRITE',
    ADD COLUMN relationship_generation bigint NOT NULL DEFAULT 1,
    ADD CONSTRAINT asset_authorization_operation_check CHECK (
        operation IN ('WRITE', 'DELETE')
    ),
    ADD CONSTRAINT asset_authorization_generation_check CHECK (
        relationship_generation > 0
    ),
    ADD CONSTRAINT uq_asset_authorization_operation UNIQUE (
        asset_id,
        relationship_generation,
        operation,
        tuple_user,
        tuple_relation,
        tuple_object
    );

CREATE TABLE public.asset_skill_activations (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    user_id uuid NOT NULL,
    enabled boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT asset_skill_activations_pkey PRIMARY KEY (id),
    CONSTRAINT uq_asset_skill_activation UNIQUE (asset_id, user_id),
    CONSTRAINT fk_asset_skill_activation_asset FOREIGN KEY (asset_id, organization_id)
        REFERENCES public.assets(id, organization_id),
    CONSTRAINT fk_asset_skill_activation_user FOREIGN KEY (user_id, organization_id)
        REFERENCES public.app_users(id, organization_id)
);

CREATE INDEX idx_asset_skill_activation_user
    ON public.asset_skill_activations (organization_id, user_id, enabled);
