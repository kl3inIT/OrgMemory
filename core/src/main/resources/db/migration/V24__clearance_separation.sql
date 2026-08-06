ALTER TABLE public.app_users
    RENAME COLUMN role TO clearance;

UPDATE public.app_users
SET clearance = CASE
    WHEN clearance = 'EXECUTIVE' THEN 'EXECUTIVE'
    ELSE 'STANDARD'
END;

ALTER TABLE public.app_users
    ALTER COLUMN clearance TYPE character varying(32),
    ADD CONSTRAINT chk_app_user_clearance
        CHECK (clearance IN ('STANDARD', 'EXECUTIVE'));

ALTER TABLE public.user_invitations
    DROP CONSTRAINT chk_user_invitation_role;

ALTER TABLE public.user_invitations
    RENAME COLUMN role TO clearance;

UPDATE public.user_invitations
SET clearance = CASE
    WHEN clearance = 'EXECUTIVE' THEN 'EXECUTIVE'
    ELSE 'STANDARD'
END;

ALTER TABLE public.user_invitations
    ADD CONSTRAINT chk_user_invitation_clearance
        CHECK (clearance IN ('STANDARD', 'EXECUTIVE'));
