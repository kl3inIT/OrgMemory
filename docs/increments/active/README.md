# Active Increments

Each active increment has `design.md` and `plan.md`. Keep only coherent work in
progress here. Consolidate current behavior before moving an increment to
`../completed`.

## Current Queue

1. Complete the
   [production ZM runtime](2026-07-25-production-cicd-zm/plan.md): disable the
   obsolete runner, take and restore-test backups, perform the bounded shared
   PostgreSQL cutover, bring up the runtime, and prove login, upload, GraphRAG,
   citation, denial, rollback, and resource behavior.
2. Complete the reproducible demo through the real ingestion API and run the
   permission evaluation dataset.
3. Prove the Slack connector against a real workspace, including member removal
   and the next-crawl access revocation.
4. In the isolated identity worktree, land the accepted tenant-hardening
   increment before exposing any native SCIM endpoint.
5. Complete
   [Skill authoring and Draft publication](2026-07-27-skill-authoring-publication/plan.md):
   folder-first local validation, deterministic packaging, and authenticated
   Draft creation while keeping MCP tools read-only.

## Native Identity Provisioning Program

The selected planning direction keeps Keycloak as the OIDC/SAML broker and
builds a tenant-bound OrgMemory SCIM service provider.
[ADR 0016](../../decisions/0016-native-scim-behind-keycloak-broker.md) was
accepted on 2026-07-27 by explicit project-owner direction after the attempted
Claude Fable 5 review was blocked by the account spend limit. H1 implementation
is active; no native SCIM capability may be exposed before its dependency gates
pass.

```text
H1-H4  Identity tenant hardening and compatibility cutover
  -> F1-F3  Provisioning ledger, machine security, discovery
  -> U0-U3  Invitation guard, immutable correlation, SCIM Users
  -> G1-G3  Inert Directory Groups
  -> A1-A3  Explicit Group authorization mapping (optional GA scope)
  -> O1-O3  Vendor conformance, operations, restore, canary
```

The dependency-ordered increments are:

1. [Identity tenant hardening](../completed/2026-07-27-identity-tenant-hardening/design.md):
   repair organization-scoped identity integrity and concurrent
   issuer/subject binding before SCIM.
2. [SCIM provisioning foundation](2026-07-27-scim-provisioning-foundation/design.md):
   select the protocol/parser dependency, add the provisioning ledger, split
   local and directory lifecycle, create tenant-bound credentials, and expose
   truthful authenticated discovery.
3. [SCIM User lifecycle private beta](2026-07-27-scim-user-lifecycle/design.md):
   ship User CRUD/search/PATCH/tombstone and bind first login only through a
   trusted immutable workforce-key claim, never email.
4. [SCIM Directory Groups](2026-07-27-scim-directory-groups/design.md):
   mirror direct directory membership while proving that it grants no
   application or source access.
5. [Directory Group authorization mapping](2026-07-27-directory-group-authorization/design.md):
   add an explicit impact-previewed policy and an ownership-safe OpenFGA
   outbox. This increment is optional if the GA product offers inert groups
   only.
6. [SCIM operations and certification](2026-07-27-scim-operations-certification/design.md):
   prove Entra and Okta interoperability, measured limits, monitoring,
   credential response, backup/restore, rollback, and canary rollout.

| Increment | PRs | Runtime exposure after exit | Primary rollback lever |
| --- | --- | --- | --- |
| Tenant hardening | H1-H4 | Existing invitation/OIDC only | Recorded compatibility-floor binary |
| Foundation | F1-F3 | Authenticated discovery; mutations disabled | Disable/suspend connection |
| Users | U0-U3 | Users private beta for one organization | Read-only/suspend, previous binary |
| Directory Groups | G1-G3 | Inert Groups for approved connection | Disable Groups capability |
| Authorization mapping | A1-A3 | Approved mappings for pilot cohort | Fail-closed revoke from owned ledger |
| Certification | O1-O3 | Provider/cohort-controlled GA | Global security cutoff and restore runbook |

The inert-Groups production branch contains seventeen deliberately bounded PRs:
H1-H4, F1-F3, U0-U3, G1-G3, and O1-O3. If authorization-bearing Groups are in
the release, A1-A3 raise the program to twenty PRs and make mapping-specific
certification gates mandatory. They merge from the latest `main` in dependency
order; they are not held as one long-lived stack. Protocol fixtures and provider
setup can progress in parallel, but schema, security, Users, Groups, and
authorization projection cannot bypass their preceding merge gates.
