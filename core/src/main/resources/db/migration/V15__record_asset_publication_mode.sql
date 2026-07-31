ALTER TABLE public.asset_releases
    ADD COLUMN publication_mode text NOT NULL DEFAULT 'REVIEWED';

ALTER TABLE public.asset_releases
    ALTER COLUMN publication_mode DROP DEFAULT,
    ADD CONSTRAINT asset_release_publication_mode_check
        CHECK (publication_mode IN ('REVIEWED', 'DIRECT')) NOT VALID;

ALTER TABLE public.asset_releases
    VALIDATE CONSTRAINT asset_release_publication_mode_check;
