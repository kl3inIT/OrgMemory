ALTER TABLE public.scim_user_resources
    DROP CONSTRAINT uq_scim_user_app_user;

ALTER TABLE public.scim_user_resources
    ADD CONSTRAINT uq_scim_user_app_user
        UNIQUE (organization_id, app_user_id);
