DO $$
DECLARE
    duplicate_organization_emails bigint;
BEGIN
    SELECT count(*)
    INTO duplicate_organization_emails
    FROM (
        SELECT app_user.organization_id, lower(app_user.email)
        FROM public.app_users app_user
        GROUP BY app_user.organization_id, lower(app_user.email)
        HAVING count(*) > 1
    ) duplicates;

    IF duplicate_organization_emails > 0 THEN
        RAISE EXCEPTION USING
            ERRCODE = '23505',
            MESSAGE = format(
                'organization email cutover preflight failed: duplicate_organization_emails=%s',
                duplicate_organization_emails);
    END IF;
END
$$;

CREATE UNIQUE INDEX uq_app_users_organization_email_lower
    ON public.app_users (organization_id, lower(email));

DROP INDEX public.uq_app_users_email_lower;
