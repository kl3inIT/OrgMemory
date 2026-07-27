DO $$
DECLARE
    duplicate_organization_emails bigint;
    invalid_app_user_departments bigint;
    invalid_invitation_departments bigint;
    invalid_invitation_inviters bigint;
    invalid_invitation_accepted_users bigint;
BEGIN
    SELECT count(*)
    INTO duplicate_organization_emails
    FROM (
        SELECT app_user.organization_id, lower(app_user.email)
        FROM public.app_users app_user
        GROUP BY app_user.organization_id, lower(app_user.email)
        HAVING count(*) > 1
    ) duplicates;

    SELECT count(*)
    INTO invalid_app_user_departments
    FROM public.app_users app_user
    JOIN public.departments department
      ON department.id = app_user.department_id
    WHERE department.organization_id <> app_user.organization_id;

    SELECT count(*)
    INTO invalid_invitation_departments
    FROM public.user_invitations invitation
    JOIN public.departments department
      ON department.id = invitation.department_id
    WHERE department.organization_id <> invitation.organization_id;

    SELECT count(*)
    INTO invalid_invitation_inviters
    FROM public.user_invitations invitation
    JOIN public.app_users inviter
      ON inviter.id = invitation.invited_by_user_id
    WHERE inviter.organization_id <> invitation.organization_id;

    SELECT count(*)
    INTO invalid_invitation_accepted_users
    FROM public.user_invitations invitation
    JOIN public.app_users accepted_user
      ON accepted_user.id = invitation.accepted_app_user_id
    WHERE accepted_user.organization_id <> invitation.organization_id;

    IF duplicate_organization_emails > 0
       OR invalid_app_user_departments > 0
       OR invalid_invitation_departments > 0
       OR invalid_invitation_inviters > 0
       OR invalid_invitation_accepted_users > 0 THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = format(
                'identity tenant integrity preflight failed: duplicate_organization_emails=%s, app_user_department=%s, invitation_department=%s, invitation_inviter=%s, invitation_accepted_user=%s',
                duplicate_organization_emails,
                invalid_app_user_departments,
                invalid_invitation_departments,
                invalid_invitation_inviters,
                invalid_invitation_accepted_users);
    END IF;
END
$$;

ALTER TABLE public.app_users
    ADD CONSTRAINT fk_app_user_department_organization
    FOREIGN KEY (department_id, organization_id)
    REFERENCES public.departments (id, organization_id)
    NOT VALID;

ALTER TABLE public.user_invitations
    ADD CONSTRAINT fk_user_invitation_department_organization
    FOREIGN KEY (department_id, organization_id)
    REFERENCES public.departments (id, organization_id)
    NOT VALID;

ALTER TABLE public.user_invitations
    ADD CONSTRAINT fk_user_invitation_inviter_organization
    FOREIGN KEY (invited_by_user_id, organization_id)
    REFERENCES public.app_users (id, organization_id)
    NOT VALID;

ALTER TABLE public.user_invitations
    ADD CONSTRAINT fk_user_invitation_accepted_user_organization
    FOREIGN KEY (accepted_app_user_id, organization_id)
    REFERENCES public.app_users (id, organization_id)
    NOT VALID;

ALTER TABLE public.app_users
    VALIDATE CONSTRAINT fk_app_user_department_organization;

ALTER TABLE public.user_invitations
    VALIDATE CONSTRAINT fk_user_invitation_department_organization;

ALTER TABLE public.user_invitations
    VALIDATE CONSTRAINT fk_user_invitation_inviter_organization;

ALTER TABLE public.user_invitations
    VALIDATE CONSTRAINT fk_user_invitation_accepted_user_organization;

ALTER TABLE public.app_users
    DROP CONSTRAINT app_users_department_id_fkey;

ALTER TABLE public.user_invitations
    DROP CONSTRAINT user_invitations_department_id_fkey,
    DROP CONSTRAINT user_invitations_invited_by_user_id_fkey,
    DROP CONSTRAINT user_invitations_accepted_app_user_id_fkey;
