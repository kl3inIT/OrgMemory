# Secure Retrieval Spec

Source: `core/src/main/java/com/orgmemory/core/knowledge`,
`core/src/main/java/com/orgmemory/core/permission`,
`apps/api/src/main/java/com/orgmemory/api/knowledge`, and
`integrations/authorization-openfga`.

Reconciled: `2026-08-01-spring-modulith-package-refactor (ce1a970b)`.

## Current Behavior

Knowledge search evaluates tenant, the stable Knowledge Asset's current-version
pointer, active immutable version, current source revision, ingestion ACL,
current ACL head, applied publication/model/profile generation, OrgMemory
policy, and classification. OpenFGA `ListObjects` supplies candidate stable
asset IDs. SQL applies every canonical predicate before PostgreSQL FTS,
pgvector, or graph ranking and before model context assembly. OpenFGA
`BatchCheck` and canonical SQL rechecks guard selected evidence. Every serving
surface performs that batch recheck through one shared collaborator with a
mandatory typed result policy: hybrid search filters denied evidence and may
return a partial result, while citation opens and the GraphRAG final closure
require every decision allowed and fail the whole request otherwise. Each
surface keeps its own deny-reason and exception mapping. The resulting
evidence set is the immutable authorization snapshot for one Assistant turn.
Only evidence that fits the model-context budget is exposed as a citation, and
answer tokens stream without a post-generation authorization replay. A
revocation applies to every new turn; an already-started turn may finish under
its request snapshot and is bounded by the configured two-minute turn timeout.

Parent Knowledge exposes the permission-aware query, immutable evidence,
secure result, and verified grounding through the exact `knowledge::search`
named interface. Assistant and Asset Registry cross that interface; the open
Retrieval nested module retains the concrete engines, authorization sequence,
ranking, and persistence while its remaining adapter seams are closed.
Asset existence, active authorization-scope, and current catalog reads cross
one Asset-owned query that keeps tenant and lifecycle predicates behind the
closed Asset module; Retrieval imports neither Asset repository.

Citation URLs are opaque API routes, not object-storage URLs. Opening one reruns
the current canonical evidence boundary once, validates the revision and blob
integrity, and streams the original bytes through the authenticated API with
`no-store` and `nosniff`. Missing and denied citation reads return the same
generic `404`. A missing control-plane role or incomplete current actor is
rejected at the request boundary with `403`.

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
current generation; superseded or absent current evidence fails closed. The
latest sealed `COMPLETE` source ACL remains authoritative after its freshness
timestamp; expiry is connector-health evidence rather than a universal
authorization denial, consistently in canonical retrieval and PostgreSQL
GraphRAG. Audit records include request/decision context and exact snapshot IDs;
raw query text is represented only by a hash.

OIDC identities resolve only through an explicit issuer/subject binding; email
and identity-provider roles never bootstrap access. Knowledge ACL principals
include namespaced OrgMemory users, departments, and organizations plus verified
external source users/groups resolved through the mapping ledger. The in-app
Assistant and read-only MCP tool use the same GraphRAG application service.
Graph-assisted retrieval, graph exploration, citation streaming, and export all
reuse the same authorized evidence scope; no graph node or relation owns an
independent ACL. Multi-source derived-permission intersection remains open.

## Source Modules

- `core.permission`
- `core.knowledge`
- `apps/api.knowledge`
- `apps/api.security`

## Related Decisions

- [0003](../../decisions/0003-postgresql-ledger-openfga-authorization.md)
- [0009](../../decisions/0009-dynamic-source-acl-ceiling.md)
- [0012](../../decisions/0012-stable-knowledge-assets-and-immutable-versions.md)
