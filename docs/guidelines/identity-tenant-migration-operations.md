# Identity Tenant Migration Operations

Use these checks before deploying the identity tenant-hardening migrations.
They report only counts and do not repair ownership.

```sql
SELECT organization_id, lower(email), count(*)
FROM app_users
GROUP BY organization_id, lower(email)
HAVING count(*) > 1;

SELECT count(*) AS cross_tenant_user_departments
FROM app_users app_user
JOIN departments department ON department.id = app_user.department_id
WHERE department.organization_id <> app_user.organization_id;

SELECT count(*) AS cross_tenant_invitation_references
FROM user_invitations invitation
LEFT JOIN departments department ON department.id = invitation.department_id
LEFT JOIN app_users inviter ON inviter.id = invitation.invited_by_user_id
LEFT JOIN app_users accepted ON accepted.id = invitation.accepted_app_user_id
WHERE (department.id IS NOT NULL
       AND department.organization_id <> invitation.organization_id)
   OR inviter.organization_id <> invitation.organization_id
   OR (accepted.id IS NOT NULL
       AND accepted.organization_id <> invitation.organization_id);
```

A non-zero result blocks deployment and requires an explicitly reviewed data
remediation. Never infer organization ownership from email.

For a binding conflict, inspect only the affected issuer and subject through an
authorized operational session. `identity.binding-subject-conflict` means the
subject already owns another app user; `identity.binding-user-conflict` means
that user already has another subject for the issuer; and
`identity.binding-race-unresolved` means the insert winner could not be proven.
Do not copy foreign user or organization identifiers into client-visible
errors.

V8 and V9 are forward-only. Before V9, the global email index remains the
compatibility floor. After V9, commit `daa5c1b` or later is required because
older global readers cannot represent duplicate email addresses across
organizations. Recovery below that floor is roll-forward or database restore,
not a down migration.
