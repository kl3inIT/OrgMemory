ALTER TABLE public.asset_releases
    ADD COLUMN publication_mode varchar(16) NOT NULL DEFAULT 'REVIEWED';

ALTER TABLE public.asset_releases
    ALTER COLUMN publication_mode DROP DEFAULT,
    ADD CONSTRAINT asset_release_publication_mode_check
        CHECK (publication_mode IN ('REVIEWED', 'DIRECT'));
