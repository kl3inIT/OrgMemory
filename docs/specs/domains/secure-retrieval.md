# Secure Retrieval Spec

## Current Behavior

Knowledge search evaluates tenant, the stable Knowledge Asset's current-version
pointer, active immutable version, current source revision, ingestion ACL,
current ACL head, applied publication/model/profile generation, OrgMemory
policy, and classification. OpenFGA `ListObjects` supplies the authorized stable
asset IDs. SQL applies every canonical predicate before PostgreSQL FTS +
pgvector ranking and `LIMIT`; OpenFGA `BatchCheck` and Java canonical rechecks
guard returned citations. Missing and denied resource reads for an otherwise
eligible reader return the same generic `404`. A missing control-plane role or
incomplete current actor is rejected at the request boundary with `403`.

Control-plane roles (`ADMIN`, `REVIEWER`, `CONTRIBUTOR`, `VIEWER`) are separate
from knowledge roles (`EMPLOYEE`, `MANAGER`, `DIRECTOR`, `EXECUTIVE`). Admin does
not imply Executive or source access. Classification requires:

| Classification | Declared scope | Employee/Manager/Director | Executive |
| --- | --- | --- | --- |
| Public | All | allow | allow |
| Internal | All Employees | allow | allow |
| Confidential | Own Department | own department only | cross-department allow |
| Restricted | Executive Only | deny | allow |

Every classification decision is still intersected with tenant, both source ACL
snapshots, and OrgMemory policy.

ACL snapshots are immutable and sealed. A compare-and-set head selects the
current generation; stale or absent current evidence fails closed. The current
head expires after 24 hours while the ingestion snapshot remains a historical
ceiling. Audit records include request/decision context and exact snapshot IDs;
raw query text is represented only by a hash.

OIDC identities resolve only through an explicit issuer/subject binding; email
and identity-provider roles never bootstrap access. Knowledge ACL principals
include namespaced OrgMemory users, departments, and organizations plus verified
external source users/groups resolved through the mapping ledger. Hybrid
FTS/pgvector retrieval and the in-app Assistant are implemented. Graph-assisted
retrieval, MCP delivery, export, and multi-source derived-permission
intersection are not yet wired.

## Source Modules

- `core.permission`
- `core.knowledge`
- `apps/api.knowledge`
- `apps/api.security`

## Related Decisions

- [0003](../../decisions/0003-postgresql-ledger-openfga-authorization.md)
- [0009](../../decisions/0009-dynamic-source-acl-ceiling.md)
- [0012](../../decisions/0012-stable-knowledge-assets-and-immutable-versions.md)
