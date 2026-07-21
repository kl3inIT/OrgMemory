# Secure Retrieval Spec

## Current Behavior

Knowledge list/search and detail evaluate tenant, lifecycle, ingestion ACL,
current ACL head, OrgMemory policy, and classification. SQL applies the predicate
before keyword matching and `LIMIT`; Java rechecks returned rows. Missing and
denied resource reads for an otherwise eligible reader return the same generic
`404`. A missing control-plane role or incomplete current actor is rejected at
the request boundary with `403`.

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

OIDC issuer/subject external identity linking exists after verified-email
bootstrap. Supported knowledge ACL principals are still explicitly namespaced
OrgMemory users, departments, and organizations. Search is keyword-only.
OpenFGA, external source-group/principal mapping,
pgvector/hybrid retrieval, graph retrieval, Ask Memory, MCP, web, export, and
multi-source derived permissions are not part of the implemented path.

## Source Modules

- `core.permission`
- `core.knowledge`
- `apps/api.knowledge`
- `apps/api.security`

## Related Decisions

- [0003](../../decisions/0003-postgresql-ledger-openfga-authorization.md)
