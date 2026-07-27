ALTER TABLE public.assets
    DROP CONSTRAINT assets_type_check;

ALTER TABLE public.assets
    ADD CONSTRAINT assets_type_check CHECK (asset_type IN (
        'PROMPT_TEMPLATE', 'WORK_INSTRUCTION', 'CAPABILITY_PACK', 'SKILL'
    ));

ALTER TABLE public.asset_payload_references
    ADD COLUMN draft_id uuid,
    ADD COLUMN content_length bigint;

ALTER TABLE public.asset_drafts
    ADD CONSTRAINT uq_asset_draft_id_organization
        UNIQUE (id, organization_id);

ALTER TABLE public.asset_payload_references
    DROP CONSTRAINT asset_payload_owner_check;

ALTER TABLE public.asset_payload_references
    ADD CONSTRAINT asset_payload_owner_check CHECK (
        (owner_kind = 'DRAFT'
            AND draft_id IS NOT NULL
            AND revision_id IS NULL
            AND release_id IS NULL)
        OR
        (owner_kind = 'REVISION'
            AND draft_id IS NULL
            AND revision_id IS NOT NULL
            AND release_id IS NULL)
        OR
        (owner_kind = 'RELEASE'
            AND draft_id IS NULL
            AND revision_id IS NULL
            AND release_id IS NOT NULL)
    ),
    ADD CONSTRAINT asset_payload_reference_length_check CHECK (
        content_length IS NULL OR content_length > 0
    ),
    ADD CONSTRAINT asset_payload_reference_blob_metadata_check CHECK (
        reference_kind <> 'BLOB'
        OR (
            digest IS NOT NULL
            AND media_type IS NOT NULL
            AND content_length IS NOT NULL
        )
    ),
    ADD CONSTRAINT fk_asset_payload_draft
        FOREIGN KEY (draft_id, organization_id)
        REFERENCES public.asset_drafts(id, organization_id);

CREATE UNIQUE INDEX uq_asset_payload_reference_draft
    ON public.asset_payload_references (organization_id, draft_id)
    WHERE draft_id IS NOT NULL;

CREATE UNIQUE INDEX uq_asset_payload_reference_revision
    ON public.asset_payload_references (organization_id, revision_id)
    WHERE revision_id IS NOT NULL;

CREATE UNIQUE INDEX uq_asset_payload_reference_release
    ON public.asset_payload_references (organization_id, release_id)
    WHERE release_id IS NOT NULL;
